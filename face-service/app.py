import secrets
import logging
from time import monotonic
from functools import lru_cache
import os

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI()
logger = logging.getLogger("face-service")
app.add_middleware(CORSMiddleware, allow_origins=[os.getenv("FRONTEND_ORIGIN", "http://localhost:5173")], allow_methods=["POST"], allow_headers=["*"], allow_credentials=True)

MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")
DETECTOR = os.path.join(MODEL_DIR, "face_detection_yunet_2023mar.onnx")
RECOGNIZER = os.path.join(MODEL_DIR, "face_recognition_sface_2021dec.onnx")




# One worker owns the short-lived challenges and one-use proofs.
sessions = {}
proofs = {}


def prune():
    now = monotonic()
    for store in (sessions, proofs):
        for key in [key for key, value in store.items() if value["expires"] <= now]:
            del store[key]


@app.post("/challenge/{purpose}")
async def start(purpose: str):
    prune()
    if purpose not in ("enroll", "identify"):
        raise HTTPException(400, "Mục đích quét không hợp lệ.")
    models()
    if len(sessions) >= 256:
        raise HTTPException(429, "Có nhiều lượt quét. Vui lòng thử lại.")
    first, second = secrets.choice([("left", "right"), ("right", "left")])
    key = secrets.token_urlsafe(32)
    sessions[key] = dict(steps=["front", first, second], index=0,
                         hits=0, misses=0, reference=None, expires=monotonic()+60, purpose=purpose)
    return {"challenge": key, "pose": "front", "completed": 0}


@lru_cache(maxsize=1)
def models():
    if not all(os.path.isfile(path) for path in (DETECTOR, RECOGNIZER)):
        raise HTTPException(503, "Chưa cài mô hình nhận diện khuôn mặt.")
    try:
        return cv2.FaceDetectorYN.create(DETECTOR, "", (640, 480)), cv2.FaceRecognizerSF.create(RECOGNIZER, "")
    except (cv2.error, AttributeError, OSError) as error:
        logger.exception("Không thể khởi tạo mô hình ONNX")
        raise HTTPException(503, "Không thể tải mô hình ONNX. Hãy kiểm tra phiên bản OpenCV và hai tệp trong face-service/models.") from error


def observe(frame):
    detector, recognizer = models()
    try:
        detector.setInputSize((frame.shape[1], frame.shape[0]))
        _, faces = detector.detect(frame)
    except cv2.error as error:
        logger.exception("OpenCV không xử lý được khung hình %s", frame.shape)
        raise HTTPException(422, "Không xử lý được khung hình camera. Hãy thử lại với camera đủ sáng.") from error
    if faces is None or len(faces) != 1:
        return None
    face = faces[0]
    eyes = face[4:8].reshape(2, 2)
    axis = eyes[1] - eyes[0]
    span = np.linalg.norm(axis)
    if span < 25:
        return None
    # Project onto the eye line so tilting the image cannot substitute for yaw.
    displacement = np.dot(face[8:10] - eyes.mean(axis=0), axis) / span**2
    pose = "front" if abs(displacement) <= .12 else "left" if displacement >= .20 else "right" if displacement <= -.20 else None
    try:
        vector = recognizer.feature(recognizer.alignCrop(frame, face))[0]
    except cv2.error as error:
        logger.exception("OpenCV không căn chỉnh được khuôn mặt")
        raise HTTPException(422, "Không căn chỉnh được khuôn mặt. Hãy nhìn thẳng và thử lại.") from error
    return pose, vector / np.linalg.norm(vector)


@app.post("/frame/{key}")
async def advance(key: str, image: UploadFile = File(...)):
    data = await image.read(500001)
    prune()
    state = sessions.get(key)
    if not state:
        raise HTTPException(410, "Lượt quét đã hết hạn. Vui lòng quét lại.")
    if not data or len(data) > 500000:
        raise HTTPException(400, "Khung hình không hợp lệ.")
    frame = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(400, "Không đọc được khung hình.")
    observation = observe(frame)
    target = state["steps"][state["index"]]
    if observation is None:
        state["misses"] += 1
        if state["reference"] is None:
            state["hits"] = 0
            return {"pose": target, "completed": state["index"], "hint": "Đưa một khuôn mặt rõ nét vào giữa khung."}
        if state["misses"] < 5:
            return {"pose": target, "completed": state["index"], "hint": "Chưa thấy rõ khuôn mặt. Hãy nhìn vào camera và giữ khuôn mặt trong khung."}
        del sessions[key]
        raise HTTPException(422, "Không nhận diện được khuôn mặt trong nhiều khung hình liên tiếp. Hãy đứng gần camera, đủ sáng và quét lại.")
    state["misses"] = 0
    pose, vector = observation
    reference = state["reference"]
    # Side views can be much less similar to a frontal image of the same person.
    # The frontal image remains the identity sample used for Elo matching.
    minimum_similarity = .45 if pose == "front" else .20
    if reference is not None and np.dot(vector, reference) < minimum_similarity:
        del sessions[key]
        raise HTTPException(422, "Khuôn mặt đã thay đổi. Vui lòng quét lại.")
    if pose in ("left", "right") and target in ("left", "right") and pose != target:
        state["hits"] = 0
        direction = "trái" if target == "left" else "phải"
        detected = "phải" if pose == "right" else "trái"
        return {"pose": target, "completed": state["index"],
                "hint": f"Bạn đang quay sang {detected}. Hãy quay sang {direction} của bạn."}
    if pose == "front" and target != "front":
        state["hits"] = 0
        direction = "trái" if target == "left" else "phải"
        return {"pose": target, "completed": state["index"], "hint": f"Hãy quay đầu sang {direction} của bạn."}
    if pose == target and reference is None:
        state["reference"] = vector
    state["hits"] = state["hits"] + 1 if pose == target else 0
    if state["hits"] >= 2:
        state["hits"] = 0
        state["index"] += 1
    if state["index"] < len(state["steps"]):
        return {"pose": state["steps"][state["index"]], "completed": state["index"]}
    del sessions[key]
    proof = secrets.token_urlsafe(32)
    proofs[proof] = dict(vector=reference.tolist(), purpose=state["purpose"], expires=monotonic()+180)
    return {"proof": proof, "completed": len(state["steps"])}


@app.post("/consume/{purpose}")
async def consume(purpose: str, request: dict):
    prune()
    key = request.get("proof")
    record = proofs.get(key) if isinstance(key, str) else None
    if not record or record["purpose"] != purpose:
        raise HTTPException(403, "Lượt quét không hợp lệ hoặc đã hết hạn.")
    del proofs[key]
    return record["vector"]
