import io
import unittest
from unittest.mock import patch

import cv2
import numpy as np
from fastapi import HTTPException, UploadFile

import app


class ChallengeTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.key = (await app.start("identify"))["challenge"]
        self.frame = cv2.imencode(".jpg", np.zeros((240, 320, 3), np.uint8))[1].tobytes()
        self.vector = np.eye(1, 128)[0]

    async def send(self, pose, vector=None):
        with patch.object(app, "observe", return_value=(pose, self.vector if vector is None else vector)):
            return await app.advance(self.key, UploadFile(io.BytesIO(self.frame)))

    async def test_order_static_and_one_use(self):
        self.assertEqual(app.sessions[self.key]["index"], 0)
        for _ in range(8):
            step = await self.send("front")
        self.assertIn(step["pose"], ("left", "right"))
        self.assertEqual(step["completed"], 1)
        wrong = "left" if step["pose"] == "right" else "right"
        for _ in range(2):
            response = await self.send(wrong)
            self.assertEqual(response["pose"], step["pose"])
            self.assertIn("Hãy quay sang", response["hint"])
        self.assertEqual(app.sessions[self.key]["index"], 1)
        for completed, pose in [(2, step["pose"]), (3, wrong)]:
            await self.send(pose)
            result = await self.send(pose)
            self.assertEqual(result["completed"], completed)
        with self.assertRaises(HTTPException):
            await app.consume("enroll", result)
        self.assertEqual(await app.consume("identify", result), self.vector.tolist())
        with self.assertRaises(HTTPException):
            await app.consume("identify", result)

    async def test_person_changes(self):
        await self.send("front")
        with self.assertRaises(HTTPException):
            await self.send("front", np.roll(self.vector, 1))
        self.assertNotIn(self.key, app.sessions)

    async def test_side_view_keeps_frontal_identity_sample(self):
        await self.send("front")
        step = await self.send("front")
        side_vector = self.vector * .3 + np.roll(self.vector, 1) * np.sqrt(1 - .3**2)
        first_side = step["pose"]
        second_side = "left" if first_side == "right" else "right"
        for pose in (first_side, first_side, second_side, second_side):
            result = await self.send(pose, side_vector)
        self.assertEqual(result["completed"], 3)
        self.assertEqual(await app.consume("identify", result), self.vector.tolist())

    async def test_face_lost(self):
        await self.send("front")
        with self.assertRaises(HTTPException):
            await app.advance(self.key, UploadFile(io.BytesIO(self.frame)))
        self.assertNotIn(self.key, app.sessions)
