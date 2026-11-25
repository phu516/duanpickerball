package com.pickleball.elo;

import com.pickleball.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EloController {
    private final JdbcTemplate jdbc;
    private final Map<String, Ticket> tickets = new HashMap<>();
    private final Map<Long, String> active = new HashMap<>();
    private final Map<String, Pass> verified = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, PendingFace> pendingFaces = new java.util.concurrent.ConcurrentHashMap<>();

    private record Pass(long playerId, long accountId, long expires) {}
    private record PendingFace(double[] embedding, long expires) {}
    private final LiveFaceVerifier verifier;

    public EloController(JdbcTemplate jdbc, LiveFaceVerifier verifier) { this.jdbc = jdbc; this.verifier = verifier; }

    @GetMapping("/admin/elo/players")
    public List<Player> players() { return jdbc.query("select id,full_name,phone,experience,pickleball_months,other_sport,rating,games_played from elo_players order by rating desc, id asc", (rs, n) -> playerRow(rs)); }

    @GetMapping("/admin/elo/face-duplicates")
    public List<FaceDuplicate> faceDuplicates() {
        record Sample(long id, String name, double[] vector) {}
        var samples = jdbc.query("select id,full_name,face_embedding from elo_players where face_embedding is not null",
                (rs, n) -> new Sample(rs.getLong(1), rs.getString(2), parse(rs.getString(3))));
        List<FaceDuplicate> duplicates = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++)
            for (int j = i + 1; j < samples.size(); j++)
                if (similarity(samples.get(i).vector(), samples.get(j).vector()) >= 0.36)
                    duplicates.add(new FaceDuplicate(samples.get(i).id(), samples.get(i).name(), samples.get(j).id(), samples.get(j).name()));
        return duplicates;
    }

    @PostMapping("/admin/elo/face-check")
    @Transactional
    public FaceCheckResult checkFace(@RequestBody FaceCheckRequest request) {
        double[] embedding = verifier.consume("enroll", request.proof());
        checkEmbedding(embedding);
        lockFaceRegistration();
        Long excludeId = request.excludePlayerId();
        List<FaceOwner> matches = matchingPlayers(embedding, 0.36).stream()
                .filter(id -> excludeId == null || !excludeId.equals(id))
                .map(id -> player(id))
                .filter(java.util.Objects::nonNull)
                .map(existing -> new FaceOwner(existing.id(), existing.fullName()))
                .toList();
        if (!matches.isEmpty()) return new FaceCheckResult(true, matches, null);

        long now = System.currentTimeMillis();
        pendingFaces.values().removeIf(pending -> pending.expires() <= now);
        String scanToken = UUID.randomUUID().toString();
        pendingFaces.put(scanToken, new PendingFace(embedding, now + 180_000));
        return new FaceCheckResult(false, List.of(), scanToken);
    }

    @GetMapping("/admin/elo/matches")
    public List<Match> matches() { return jdbc.query("select id,player_a_id,player_b_id,status,score_a,score_b from elo_matches order by id desc", (rs,n) -> new Match(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),(Integer)rs.getObject(5),(Integer)rs.getObject(6))); }

    @GetMapping("/admin/elo/registrations")
    public List<Registrant> registrants() {
        return jdbc.query("select r.id,r.full_name,r.phone,p.id from tournament_registrations r left join elo_players p on p.phone=r.phone order by r.id desc",
                (rs,n) -> new Registrant(rs.getLong(1),rs.getString(2),rs.getString(3),(Long)rs.getObject(4)));
    }

    @PostMapping("/admin/elo/players")
    @Transactional
    public Player register(@RequestBody Registration request) {
        String name = request.fullName() == null ? "" : request.fullName().trim();
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (name.length() > 120 || !name.matches("[\\p{L}\\p{M}](?:[\\p{L}\\p{M} .'-]*[\\p{L}\\p{M}])?")) throw new IllegalArgumentException("Họ tên chỉ được chứa chữ và dấu cách hợp lệ.");
        if (!phone.matches("(?:0[0-9]{9}|\\+84[0-9]{9})")) throw new IllegalArgumentException("Số điện thoại phải có 10 chữ số bắt đầu bằng 0 hoặc +84.");
        String localPhone = phone.startsWith("+84") ? "0" + phone.substring(3) : phone;
        String internationalPhone = "+84" + localPhone.substring(1);
        if (!List.of("PICKLEBALL", "RACKET", "SPORT", "NEW").contains(request.experience())) throw new IllegalArgumentException("Chọn kinh nghiệm chơi hợp lệ.");
        if ("PICKLEBALL".equals(request.experience()) && (request.pickleballMonths() == null || request.pickleballMonths() < 0 || request.pickleballMonths() > 600)) throw new IllegalArgumentException("Nhập số tháng chơi pickleball.");
        if (("RACKET".equals(request.experience()) || "SPORT".equals(request.experience())) && (request.otherSport() == null || request.otherSport().isBlank() || request.otherSport().length() > 120)) throw new IllegalArgumentException("Nhập môn thể thao đã chơi.");
        if (jdbc.queryForObject("select count(*) from elo_players where phone in (?,?)", Integer.class, localPhone, internationalPhone) > 0) throw new BusinessException(HttpStatus.CONFLICT,"PHONE_EXISTS","Số điện thoại đã có hồ sơ. Vui lòng nhờ admin hỗ trợ xác minh.");
        double[] embedding = takeScannedFace(request.scanToken(), request.proof());
        checkEmbedding(embedding);
        lockFaceRegistration();
        List<Long> duplicates = matchingPlayers(embedding, 0.36);
        if (!duplicates.isEmpty()) throw duplicateFace(duplicates);
        jdbc.update("insert into elo_players(full_name,phone,experience,pickleball_months,other_sport,rating,games_played,created_at) values(?,?,?,?,?,?,0,?)", name,localPhone,request.experience(),request.pickleballMonths(),request.otherSport(),initialRating(request.experience(),request.pickleballMonths()),LocalDateTime.now(ZoneOffset.UTC));
        Long id = jdbc.queryForObject("select id from elo_players where phone=?",Long.class,localPhone);
        jdbc.update("update elo_players set face_embedding=? where id=?",serialize(embedding),id);
        return player(id);
    }

    @PostMapping("/elo/self-register")
    @Transactional
    public SelfRegistrationResult selfRegister(@RequestBody SelfRegistration request, Authentication authentication) {
        long accountId = accountId(authentication);
        var accounts = jdbc.queryForList("select username,full_name,phone,elo_player_id from users where id=? for update", accountId);
        if (accounts.size() != 1) throw new BusinessException(HttpStatus.UNAUTHORIZED,"LOGIN_REQUIRED","Vui lòng đăng nhập lại.");
        var account = accounts.getFirst();
        if (account.get("elo_player_id") != null) throw new BusinessException(HttpStatus.CONFLICT,"ELO_ALREADY_LINKED","Tài khoản đã có hồ sơ Elo. Vui lòng quét khuôn mặt để tiếp tục.");
        String name = account.get("full_name") == null || account.get("full_name").toString().isBlank()
                ? account.get("username").toString() : account.get("full_name").toString().trim();
        String phone = account.get("phone") == null ? "" : account.get("phone").toString().trim();
        if (!phone.isEmpty() && !phone.matches("(?:0[0-9]{9}|\\+84[0-9]{9})")) phone = "";
        if (!List.of("PICKLEBALL", "RACKET", "SPORT", "NEW").contains(request.experience())) throw new IllegalArgumentException("Chọn kinh nghiệm chơi hợp lệ.");
        double[] embedding = verifier.consume("enroll", request.proof());
        checkEmbedding(embedding);
        lockFaceRegistration();
        List<Long> matches = matchingPlayers(embedding);
        if (matchingPlayers(embedding, 0.36).size() != matches.size())
            throw new BusinessException(HttpStatus.CONFLICT,"FACE_REVIEW","Khuôn mặt có thể trùng hồ sơ Elo khác. Vui lòng nhờ admin kiểm tra.");
        if (matches.size() > 1) throw new BusinessException(HttpStatus.CONFLICT,"FACE_AMBIGUOUS","Có nhiều hồ sơ khớp khuôn mặt. Vui lòng nhờ admin hỗ trợ.");
        String localPhone = phone.isEmpty() ? null : phone.startsWith("+84") ? "0" + phone.substring(3) : phone;
        String internationalPhone = localPhone == null ? null : "+84" + localPhone.substring(1);
        Long playerId;
        if (matches.size() == 1) {
            playerId = matches.getFirst();
            var existing = player(playerId);
            if (localPhone != null && !localPhone.equals(existing.phone()) && !internationalPhone.equals(existing.phone()))
                throw new BusinessException(HttpStatus.CONFLICT,"ELO_IDENTITY_REVIEW","Khuôn mặt đã có hồ sơ với số điện thoại khác. Vui lòng nhờ admin hỗ trợ.");
        } else {
            if (localPhone != null && jdbc.queryForObject("select count(*) from elo_players where phone in (?,?)", Integer.class, localPhone, internationalPhone) > 0)
                throw new BusinessException(HttpStatus.CONFLICT,"PHONE_EXISTS","Số điện thoại đã có hồ sơ Elo. Vui lòng nhờ admin hỗ trợ xác minh.");
            var keyHolder = new GeneratedKeyHolder();
            final String savedPhone = localPhone;
            try {
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement("insert into elo_players(full_name,phone,experience,pickleball_months,other_sport,face_embedding,rating,games_played,created_at) values(?,?,?,?,?,?,?,0,?)", Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, name); statement.setString(2, savedPhone); statement.setString(3, request.experience());
                    statement.setObject(4, "PICKLEBALL".equals(request.experience()) ? 0 : null);
                    statement.setString(5, null); statement.setString(6, serialize(embedding));
                    statement.setInt(7, initialRating(request.experience(), 0)); statement.setObject(8, LocalDateTime.now(ZoneOffset.UTC));
                    return statement;
                }, keyHolder);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(HttpStatus.CONFLICT,"PHONE_EXISTS","Số điện thoại đã có hồ sơ Elo. Vui lòng nhờ admin hỗ trợ xác minh.");
            }
            playerId = keyHolder.getKey().longValue();
        }
        try {
            if (jdbc.update("update users set elo_player_id=? where id=? and elo_player_id is null", playerId, accountId) != 1)
                throw new BusinessException(HttpStatus.CONFLICT,"PLAYER_LINKED","Tài khoản đã liên kết hồ sơ Elo khác.");
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(HttpStatus.CONFLICT,"PLAYER_LINKED","Hồ sơ Elo đã liên kết với tài khoản khác.");
        }
        return new SelfRegistrationResult(player(playerId), verificationToken(playerId, accountId), matches.isEmpty());
    }

    @PostMapping("/elo/identify")
    @Transactional
    public Verification identify(@RequestBody Face face, Authentication authentication) {
        long accountId = accountId(authentication);
        double[] embedding = verifier.consume("identify", face.proof());
        checkEmbedding(embedding);
        List<Long> ids = matchingPlayers(embedding);
        if (ids.size() != 1) throw new BusinessException(HttpStatus.NOT_FOUND,"FACE_UNKNOWN","Chưa nhận diện được người chơi. Vui lòng quét lại hoặc nhờ admin hỗ trợ.");
        long playerId = ids.getFirst();
        Long linked = jdbc.query("select elo_player_id from users where id=?", rs -> rs.next() ? (Long) rs.getObject(1) : null, accountId);
        if (linked != null && linked != playerId) throw new BusinessException(HttpStatus.FORBIDDEN,"PLAYER_MISMATCH","Tài khoản đã liên kết với hồ sơ Elo khác.");
        if (linked == null) {
            try {
                if (jdbc.update("update users set elo_player_id=? where id=? and elo_player_id is null", playerId, accountId) != 1)
                    throw new BusinessException(HttpStatus.CONFLICT,"PLAYER_LINKED","Không thể liên kết hồ sơ Elo. Vui lòng thử lại.");
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(HttpStatus.CONFLICT,"PLAYER_LINKED","Hồ sơ Elo đã liên kết với tài khoản khác.");
            }
        }
        return new Verification(player(playerId),verificationToken(playerId, accountId));
    }

    @PostMapping("/admin/elo/players/{id}/face")
    @Transactional
    public Player enroll(@PathVariable long id, @RequestBody Face face) {
        double[] embedding = takeScannedFace(face.scanToken(), face.proof());
        checkEmbedding(embedding);
        if (player(id) == null) throw new BusinessException(HttpStatus.NOT_FOUND,"PLAYER_NOT_FOUND","Không tìm thấy hồ sơ Elo.");
        lockFaceRegistration();
        List<Long> duplicates = matchingPlayers(embedding, 0.36).stream().filter(candidate -> candidate != id).toList();
        if (!duplicates.isEmpty()) throw duplicateFace(duplicates);
        jdbc.update("update elo_players set face_embedding=? where id=?", serialize(embedding), id);
        return player(id);
    }

    private double[] takeScannedFace(String scanToken, String proof) {
        if (scanToken == null || scanToken.isBlank()) return verifier.consume("enroll", proof);
        PendingFace pending = pendingFaces.remove(scanToken);
        if (pending == null || pending.expires() <= System.currentTimeMillis())
            throw new BusinessException(HttpStatus.FORBIDDEN, "FACE_SCAN_EXPIRED", "Lượt quét đã hết hạn. Vui lòng quét lại.");
        return pending.embedding();
    }

    private BusinessException duplicateFace(List<Long> ids) {
        String owners = ids.stream().map(this::player).filter(java.util.Objects::nonNull)
                .map(existing -> "#" + existing.id() + " " + existing.fullName())
                .collect(java.util.stream.Collectors.joining(", "));
        return new BusinessException(HttpStatus.CONFLICT, "FACE_EXISTS",
                "Khuôn mặt này đã được đăng ký trong hồ sơ Elo " + owners + ". Không thể dùng lại khuôn mặt này.");
    }

    @PostMapping("/admin/elo/matches")
    public Match createMatch(@RequestBody Pair pair) {
        if (pair.a() == pair.b() || player(pair.a()) == null || player(pair.b()) == null) throw new IllegalArgumentException("Chọn hai người chơi có hồ sơ Elo khác nhau.");
        jdbc.update("insert into elo_matches(player_a_id,player_b_id,status,created_at) values(?,?,'READY',?)",pair.a(),pair.b(),LocalDateTime.now(ZoneOffset.UTC));
        Long id=jdbc.queryForObject("select max(id) from elo_matches",Long.class);
        return match(id);
    }

    @PutMapping("/admin/elo/matches/{id}/score")
    @Transactional
    public Match score(@PathVariable long id,@RequestBody Score score) {
        Match match=match(id);
        if (match==null) throw new BusinessException(HttpStatus.NOT_FOUND,"MATCH_NOT_FOUND","Không tìm thấy trận đấu.");
        if ("COMPLETED".equals(match.status())) throw new BusinessException(HttpStatus.CONFLICT,"MATCH_ALREADY_SCORED","Trận đấu đã được ghi kết quả.");
        if (score.a()==null || score.b()==null || score.a()<0 || score.b()<0 || score.a().equals(score.b())) throw new IllegalArgumentException("Tỷ số phải có người thắng và không âm.");
        jdbc.update("update elo_matches set score_a=?,score_b=?,status='COMPLETED',confirmed_at=? where id=?",score.a(),score.b(),LocalDateTime.now(ZoneOffset.UTC),id);
        recalculate();
        return match(id);
    }

    private void recalculate() {
        for (Player candidate : jdbc.query("select id,full_name,phone,experience,pickleball_months,other_sport,rating,games_played from elo_players", (rs,n) -> playerRow(rs))) jdbc.update("update elo_players set rating=?,games_played=0 where id=?", initialRating(candidate.experience(),candidate.pickleballMonths()),candidate.id());
        for (Match game : jdbc.query("select id,player_a_id,player_b_id,status,score_a,score_b from elo_matches where status='COMPLETED' order by confirmed_at,id",(rs,n)->new Match(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),(Integer)rs.getObject(5),(Integer)rs.getObject(6)))) {
            Player a=player(game.playerAId()), b=player(game.playerBId());
            double expected=1.0/(1+Math.pow(10,(b.rating()-a.rating())/400.0));
            int delta=(int)Math.round(32*((game.scoreA()>game.scoreB()?1:0)-expected));
            jdbc.update("update elo_players set rating=?,games_played=games_played+1 where id=?",a.rating()+delta,a.id());
            jdbc.update("update elo_players set rating=?,games_played=games_played+1 where id=?",b.rating()-delta,b.id());
        }
    }

    @PostMapping("/elo/queue")
    public synchronized QueueState queue(@RequestBody QueueRequest request, Authentication authentication) {
        long accountId = accountId(authentication);
        Pass pass = request.verificationToken() == null ? null : verified.remove(request.verificationToken());
        Long playerId = pass == null || pass.expires() <= System.currentTimeMillis() || pass.accountId() != accountId ? null : pass.playerId();
        if (playerId==null) throw new BusinessException(HttpStatus.UNAUTHORIZED,"FACE_REQUIRED","Vui lòng quét khuôn mặt để xác minh.");
        cleanup();
        String old=active.get(playerId);
        if(old!=null){Ticket previous=tickets.get(old);if(previous!=null && previous.matchId==null)return state(previous);tickets.remove(old);active.remove(playerId);}
        Ticket ticket=new Ticket(UUID.randomUUID().toString(),playerId,accountId,System.currentTimeMillis());
        tickets.put(ticket.id,ticket); active.put(ticket.playerId,ticket.id);
        Player entrant=player(ticket.playerId);
        Ticket opponent=tickets.values().stream().filter(t->t!=ticket && t.opponent==null && Math.abs(player(t.playerId).rating()-entrant.rating())<=100).min(Comparator.comparingInt((Ticket t)->Math.abs(player(t.playerId).rating()-entrant.rating())<=25?0:1).thenComparingInt(t->experienceDistance(entrant,player(t.playerId))).thenComparingInt(t->Math.abs(player(t.playerId).rating()-entrant.rating())).thenComparingLong(t->t.created)).orElse(null);
        if(opponent!=null){ ticket.opponent=opponent.id;opponent.opponent=ticket.id;ticket.deadline=System.currentTimeMillis()+30000;opponent.deadline=ticket.deadline; }
        return state(ticket);
    }

    @GetMapping("/elo/queue/{id}")
    public synchronized QueueState poll(@PathVariable String id, Authentication authentication) { cleanup(); return state(required(id, accountId(authentication))); }

    @PostMapping("/elo/queue/{id}/accept")
    public synchronized QueueState accept(@PathVariable String id, Authentication authentication) {
        Ticket t=required(id, accountId(authentication));
        if(t.opponent==null) throw new BusinessException(HttpStatus.CONFLICT,"NOT_PAIRED","Chưa tìm được đối thủ.");
        t.accepted=true;Ticket other=tickets.get(t.opponent);
        if(other.accepted && t.matchId==null){
            jdbc.update("insert into elo_matches(player_a_id,player_b_id,status,created_at) values(?,?,'READY',?)",t.playerId,other.playerId,LocalDateTime.now(ZoneOffset.UTC));
            Long matchId=jdbc.queryForObject("select max(id) from elo_matches",Long.class);
            t.matchId=matchId;other.matchId=matchId;
        }
        return state(t);
    }

    @DeleteMapping("/elo/queue/{id}")
    public synchronized void cancel(@PathVariable String id, Authentication authentication) {
        Ticket t=required(id, accountId(authentication));tickets.remove(id);active.remove(t.playerId);
        if(t.opponent!=null && tickets.containsKey(t.opponent)){Ticket other=tickets.get(t.opponent);other.opponent=null;other.accepted=false;other.deadline=0;}
    }

    private void cleanup(){long now=System.currentTimeMillis();for(Ticket t:new ArrayList<>(tickets.values())){if(t.opponent!=null && t.matchId==null && now>t.deadline){Ticket other=tickets.get(t.opponent);t.opponent=null;t.accepted=false;t.deadline=0;if(other!=null){other.opponent=null;other.accepted=false;other.deadline=0;}}if(t.matchId==null && now-t.created>600000){tickets.remove(t.id);active.remove(t.playerId);}}}
    private Ticket required(String id, long accountId){Ticket t=tickets.get(id);if(t==null)throw new BusinessException(HttpStatus.NOT_FOUND,"QUEUE_EXPIRED","Lượt tìm trận đã hết hạn.");if(t.accountId!=accountId)throw new BusinessException(HttpStatus.FORBIDDEN,"QUEUE_FORBIDDEN","Bạn không có quyền thao tác lượt tìm trận này.");return t;}
    private long accountId(Authentication authentication) {
        if (authentication == null) throw new BusinessException(HttpStatus.UNAUTHORIZED,"LOGIN_REQUIRED","Vui lòng đăng nhập để tìm trận.");
        var ids = jdbc.queryForList("select id from users where username=? and role='PLAYER' and enabled=true", Long.class, authentication.getName());
        if (ids.size()!=1) throw new BusinessException(HttpStatus.UNAUTHORIZED,"LOGIN_REQUIRED","Vui lòng đăng nhập để tìm trận.");
        return ids.getFirst();
    }
    private QueueState state(Ticket t){if(t==null)throw new BusinessException(HttpStatus.NOT_FOUND,"QUEUE_EXPIRED","Lượt tìm trận đã hết hạn.");Ticket other=t.opponent==null?null:tickets.get(t.opponent);return new QueueState(t.id,t.playerId,other==null?null:player(other.playerId),t.accepted,other!=null&&other.accepted,t.deadline,t.matchId);}
    private Player player(long id){return jdbc.query("select id,full_name,phone,experience,pickleball_months,other_sport,rating,games_played from elo_players where id=?",rs->rs.next()?playerRow(rs):null,id);}
    private Player playerRow(java.sql.ResultSet rs) throws java.sql.SQLException { int rating=rs.getInt(7); return new Player(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),(Integer)rs.getObject(5),rs.getString(6),rating,rs.getInt(8),rankFor(rating)); }
    private Match match(long id){return jdbc.query("select id,player_a_id,player_b_id,status,score_a,score_b from elo_matches where id=?",rs->rs.next()?new Match(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),(Integer)rs.getObject(5),(Integer)rs.getObject(6)):null,id);}
    private void checkEmbedding(double[] vector){if(vector==null||vector.length<64||vector.length>1024)throw new IllegalArgumentException("Dữ liệu khuôn mặt không hợp lệ.");for(double x:vector)if(!Double.isFinite(x))throw new IllegalArgumentException("Dữ liệu khuôn mặt không hợp lệ.");}
    private String serialize(double[] values){StringBuilder s=new StringBuilder();for(double x:values){if(!s.isEmpty())s.append(',');s.append(x);}return s.toString();}
    private double[] parse(String value){return java.util.Arrays.stream(value.split(",")).mapToDouble(Double::parseDouble).toArray();}
    private double similarity(double[] a,double[] b){if(a.length!=b.length)return 0;double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}return dot/(Math.sqrt(aa)*Math.sqrt(bb));}
    private List<Long> matchingPlayers(double[] embedding) { return matchingPlayers(embedding, 0.45); }
    private List<Long> matchingPlayers(double[] embedding, double threshold) {
        return jdbc.query("select id,face_embedding from elo_players where face_embedding is not null", (rs,n) -> {
            double[] stored = parse(rs.getString(2));
            return similarity(stored,embedding) >= threshold ? rs.getLong(1) : null;
        }).stream().filter(id -> id != null).toList();
    }
    private void lockFaceRegistration() {
        jdbc.queryForObject("select id from elo_face_registration_lock where id=1 for update", Integer.class);
    }
    private String verificationToken(long playerId, long accountId) {
        String token=UUID.randomUUID().toString(); verified.values().removeIf(pass -> pass.expires() <= System.currentTimeMillis());
        verified.put(token, new Pass(playerId, accountId, System.currentTimeMillis()+180000));
        return token;
    }
    private int experienceDistance(Player a,Player b){if(a.gamesPlayed()>=5||b.gamesPlayed()>=5)return 0;if(!a.experience().equals(b.experience()))return 2;if("PICKLEBALL".equals(a.experience())&&a.pickleballMonths()!=null&&b.pickleballMonths()!=null)return Math.abs(a.pickleballMonths()-b.pickleballMonths())>6?1:0;return 0;}
    private int initialRating(String experience, Integer months) {
        return switch (experience) {
            case "NEW" -> 800;
            case "SPORT" -> 900;
            case "RACKET" -> 950;
            case "PICKLEBALL" -> 1000 + Math.min(200, (months == null ? 0 : months) * 5);
            default -> 1000;
        };
    }
    public static String rankFor(int rating) {
        if (rating < 900) return "BRONZE";
        if (rating < 1000) return "SILVER";
        if (rating < 1100) return "GOLD";
        if (rating < 1200) return "PLATINUM";
        if (rating < 1300) return "DIAMOND";
        if (rating < 1400) return "MASTER";
        return "CHALLENGER";
    }
    private static class Ticket {final String id;final long playerId;final long accountId;final long created;String opponent;boolean accepted;long deadline;Long matchId;Ticket(String id,long playerId,long accountId,long created){this.id=id;this.playerId=playerId;this.accountId=accountId;this.created=created;}}
    public record Player(long id,String fullName,String phone,String experience,Integer pickleballMonths,String otherSport,int rating,int gamesPlayed,String rank){}
    public record Registrant(long registrationId,String fullName,String phone,Long playerId){}
    public record Match(long id,long playerAId,long playerBId,String status,Integer scoreA,Integer scoreB){}
    public record Registration(String fullName,String phone,String experience,Integer pickleballMonths,String otherSport,String proof,String scanToken) {
        public Registration(String fullName,String phone,String experience,Integer pickleballMonths,String otherSport,String proof) {
            this(fullName, phone, experience, pickleballMonths, otherSport, proof, null);
        }
    }
    public record FaceCheckRequest(String proof, Long excludePlayerId){}
    public record FaceCheckResult(boolean duplicate,List<FaceOwner> matches,String scanToken){}
    public record FaceOwner(long id,String fullName){}
    public record SelfRegistration(String experience,String proof){}
    public record SelfRegistrationResult(Player player,String token,boolean created){}
    public record FaceDuplicate(long firstId,String firstName,long secondId,String secondName){}
    public record Face(String proof,String scanToken) {
        public Face(String proof) { this(proof, null); }
    }
    public record Pair(long a,long b){}
    public record Score(Integer a,Integer b){}
    public record QueueRequest(String verificationToken){}
    public record Verification(Player player,String token){}
    public record QueueState(String ticketId,long playerId,Player opponent,boolean accepted,boolean opponentAccepted,long deadline,Long matchId){}
}
