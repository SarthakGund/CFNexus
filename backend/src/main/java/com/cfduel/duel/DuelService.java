package com.cfduel.duel;

import com.cfduel.cf.CfProblem;
import com.cfduel.cf.ProblemSelectionService;
import com.cfduel.rating.RatingService;
import com.cfduel.duel.dto.CreateDuelRequest;
import com.cfduel.duel.dto.CreateRoomResponse;
import com.cfduel.duel.dto.JoinDuelRequest;
import com.cfduel.duel.dto.ParticipantDto;
import com.cfduel.duel.dto.ProblemDto;
import com.cfduel.duel.dto.RoomStateDto;
import com.cfduel.user.User;
import com.cfduel.user.UserRepository;
import com.cfduel.ws.DuelBroadcaster;
import com.cfduel.ws.event.DuelEndedEvent;
import com.cfduel.ws.event.DuelStartedEvent;
import com.cfduel.ws.event.PlayerJoinedEvent;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DuelService {

    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ROOM_CODE_LENGTH = 8;
    private static final int ROOM_CODE_MAX_ATTEMPTS = 20;
    private static final int MAX_PER_TEAM = 4;
    private static final int MAX_FFA_PLAYERS = 4;

    private static final String TYPE_RATED_1V1 = "RATED_1V1";
    private static final String TYPE_UNRATED_TEAM = "UNRATED_TEAM";
    private static final String TYPE_UNRATED_FFA = "UNRATED_FFA";

    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final SecureRandom random = new SecureRandom();

    private final DuelRoomRepository roomRepository;
    private final DuelParticipantRepository participantRepository;
    private final DuelResultRepository resultRepository;
    private final UserRepository userRepository;
    private final ProblemSelectionService problemSelectionService;
    private final RatingService ratingService;
    private final DuelBroadcaster broadcaster;

    @Value("${app.frontend-origin:http://localhost:3000}")
    private String frontendOrigin;

    // ----------------------------------------------------------------------
    // Create
    // ----------------------------------------------------------------------

    @Transactional
    public CreateRoomResponse createRoom(UUID hostId, CreateDuelRequest req) {
        String type = req.type();
        String roomCode = generateUniqueRoomCode();

        DuelRoom room = DuelRoom.builder()
                .roomCode(roomCode)
                .roomType(type)
                .status(STATUS_WAITING)
                .hostId(hostId)
                .problemRating(req.problemRating())
                .build();
        room = roomRepository.save(room);

        // Host joins as the first participant.
        DuelParticipant host = DuelParticipant.builder()
                .roomId(room.getId())
                .userId(hostId)
                .status("ACTIVE")
                .build();
        if (TYPE_UNRATED_FFA.equals(type)) {
            host.setTeam(null);
            host.setSlot(1);
        } else {
            // RATED_1V1 and UNRATED_TEAM: host on team 1, slot 1.
            host.setTeam(1);
            host.setSlot(1);
        }
        participantRepository.save(host);

        String inviteUrl = frontendOrigin + "/duel/" + roomCode;
        return new CreateRoomResponse(roomCode, inviteUrl);
    }

    // ----------------------------------------------------------------------
    // Read
    // ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public RoomStateDto getRoomState(String roomCode) {
        DuelRoom room = requireRoom(roomCode);
        return toRoomState(room);
    }

    @Transactional(readOnly = true)
    public ProblemDto getProblem(String roomCode) {
        DuelRoom room = requireRoom(roomCode);
        if (room.getProblemId() == null || STATUS_WAITING.equals(room.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not assigned yet");
        }
        return toProblemDto(room);
    }

    // ----------------------------------------------------------------------
    // Join
    // ----------------------------------------------------------------------

    @Transactional
    public RoomStateDto joinRoom(String roomCode, UUID userId, JoinDuelRequest req) {
        DuelRoom room = requireRoom(roomCode);
        if (!STATUS_WAITING.equals(room.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is not accepting players");
        }

        // Idempotent: already joined -> return current state.
        Optional<DuelParticipant> existing =
                participantRepository.findByRoomIdAndUserId(room.getId(), userId);
        if (existing.isPresent()) {
            return toRoomState(room);
        }

        List<DuelParticipant> participants = participantRepository.findByRoomId(room.getId());
        String type = room.getRoomType();

        Integer team;
        Integer slot;
        switch (type) {
            case TYPE_RATED_1V1 -> {
                if (participants.size() >= 2) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
                }
                team = 2;
                slot = 1;
            }
            case TYPE_UNRATED_FFA -> {
                if (participants.size() >= MAX_FFA_PLAYERS) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
                }
                team = null;
                slot = nextFreeSlot(participants, null);
            }
            case TYPE_UNRATED_TEAM -> {
                team = chooseTeam(participants, req == null ? null : req.team());
                slot = nextFreeSlot(participants, team);
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unknown room type: " + type);
        }

        DuelParticipant participant = DuelParticipant.builder()
                .roomId(room.getId())
                .userId(userId)
                .team(team)
                .slot(slot)
                .status("ACTIVE")
                .build();
        participantRepository.save(participant);

        String handle = userRepository.findById(userId)
                .map(User::getCfHandle)
                .orElse(null);
        broadcaster.broadcast(roomCode, new PlayerJoinedEvent(userId, handle, team, slot));

        return toRoomState(room);
    }

    /** Picks the team with the fewest members (respecting an optional hint), capped at MAX_PER_TEAM. */
    private Integer chooseTeam(List<DuelParticipant> participants, Integer hint) {
        long team1 = participants.stream().filter(p -> Integer.valueOf(1).equals(p.getTeam())).count();
        long team2 = participants.stream().filter(p -> Integer.valueOf(2).equals(p.getTeam())).count();

        if (hint != null && (hint == 1 || hint == 2)) {
            long count = hint == 1 ? team1 : team2;
            if (count >= MAX_PER_TEAM) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested team is full");
            }
            return hint;
        }
        if (team1 <= team2) {
            if (team1 >= MAX_PER_TEAM) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
            }
            return 1;
        }
        if (team2 >= MAX_PER_TEAM) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
        }
        return 2;
    }

    /** First slot index (1-based) not yet used within the given team (null team = whole room). */
    private int nextFreeSlot(List<DuelParticipant> participants, Integer team) {
        List<Integer> used = participants.stream()
                .filter(p -> team == null || team.equals(p.getTeam()))
                .map(DuelParticipant::getSlot)
                .filter(s -> s != null)
                .toList();
        int slot = 1;
        while (used.contains(slot)) {
            slot++;
        }
        return slot;
    }

    // ----------------------------------------------------------------------
    // Start
    // ----------------------------------------------------------------------

    @Transactional
    public void startRoom(String roomCode, UUID hostId) {
        DuelRoom room = requireRoom(roomCode);
        if (!hostId.equals(room.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can start the duel");
        }
        if (!STATUS_WAITING.equals(room.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duel already started");
        }

        List<DuelParticipant> participants = participantRepository.findByRoomId(room.getId());
        if (TYPE_RATED_1V1.equals(room.getRoomType()) && participants.size() != 2) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A 1v1 duel needs exactly 2 players");
        }
        if (participants.size() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Not enough players to start");
        }

        List<String> handles = participantHandles(participants);
        CfProblem problem = problemSelectionService.selectProblem(handles, room.getProblemRating());

        Instant now = Instant.now();
        room.setProblemId(problem.problemKey());
        room.setProblemUrl(problem.url());
        room.setStatus(STATUS_IN_PROGRESS);
        room.setStartedAt(now);
        roomRepository.save(room);

        broadcaster.broadcast(roomCode, new DuelStartedEvent(
                problem.problemKey(), problem.name(), problem.url(), problem.rating(), now));
    }

    // ----------------------------------------------------------------------
    // End / Resign
    // ----------------------------------------------------------------------

    @Transactional
    public void endDuelWithWinner(String roomCode, UUID winnerUserId, Long solveDurationMs,
            String resultType) {
        DuelRoom room = requireRoom(roomCode);
        if (STATUS_COMPLETED.equals(room.getStatus())) {
            return; // idempotent no-op
        }

        List<DuelParticipant> participants = participantRepository.findByRoomId(room.getId());

        Integer winnerTeam = null;
        List<UUID> loserIds = new ArrayList<>();
        for (DuelParticipant p : participants) {
            if (p.getUserId().equals(winnerUserId)) {
                winnerTeam = p.getTeam();
            } else {
                loserIds.add(p.getUserId());
            }
        }

        Instant now = Instant.now();
        room.setStatus(STATUS_COMPLETED);
        room.setEndedAt(now);
        room.setWinnerTeam(winnerTeam);
        roomRepository.save(room);

        UUID singleLoser = loserIds.size() == 1 ? loserIds.get(0) : null;

        DuelResult.DuelResultBuilder resultBuilder = DuelResult.builder()
                .roomId(room.getId())
                .winnerId(winnerUserId)
                .loserId(singleLoser)
                .resultType(resultType)
                .solveDurationMs(solveDurationMs)
                .problemId(room.getProblemId());

        // Rated 1v1: run the ELO engine, which also records rating + match history.
        boolean draw = winnerUserId == null;
        if (TYPE_RATED_1V1.equals(room.getRoomType()) && participants.size() == 2) {
            if (draw) {
                User a = requireUser(participants.get(0).getUserId());
                User b = requireUser(participants.get(1).getUserId());
                ratingService.applyDraw(room, a, b);
            } else if (singleLoser != null) {
                User winner = requireUser(winnerUserId);
                User loser = requireUser(singleLoser);
                RatingService.Decisive outcome =
                        ratingService.applyDecisive(room, winner, loser, resultType, solveDurationMs);
                resultBuilder
                        .winnerRatingBefore(outcome.winnerBefore())
                        .winnerRatingAfter(outcome.winnerAfter())
                        .loserRatingBefore(outcome.loserBefore())
                        .loserRatingAfter(outcome.loserAfter())
                        .ratingDelta(outcome.delta());
            }
        }
        resultRepository.save(resultBuilder.build());

        broadcaster.broadcast(roomCode, new DuelEndedEvent(
                winnerUserId, loserIds, resultType, solveDurationMs));
    }

    @Transactional
    public void resign(String roomCode, UUID userId) {
        DuelRoom room = requireRoom(roomCode);
        if (STATUS_COMPLETED.equals(room.getStatus())) {
            return; // already over
        }
        List<DuelParticipant> participants = participantRepository.findByRoomId(room.getId());
        UUID winner = participants.stream()
                .map(DuelParticipant::getUserId)
                .filter(uid -> !uid.equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No opponent to award the win to"));
        endDuelWithWinner(roomCode, winner, null, "RESIGN");
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private DuelRoom requireRoom(String roomCode) {
        return roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < ROOM_CODE_MAX_ATTEMPTS; attempt++) {
            String code = randomRoomCode();
            if (roomRepository.findByRoomCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Could not allocate a unique room code");
    }

    private String randomRoomCode() {
        StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            sb.append(ROOM_CODE_ALPHABET.charAt(random.nextInt(ROOM_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private List<String> participantHandles(List<DuelParticipant> participants) {
        List<UUID> ids = participants.stream().map(DuelParticipant::getUserId).toList();
        Map<UUID, User> usersById = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<String> handles = new ArrayList<>();
        for (DuelParticipant p : participants) {
            User u = usersById.get(p.getUserId());
            if (u != null) {
                handles.add(u.getCfHandle());
            }
        }
        return handles;
    }

    private RoomStateDto toRoomState(DuelRoom room) {
        List<DuelParticipant> participants = participantRepository.findByRoomId(room.getId());
        List<UUID> ids = participants.stream().map(DuelParticipant::getUserId).toList();
        Map<UUID, User> usersById = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ParticipantDto> participantDtos = participants.stream()
                .map(p -> {
                    User u = usersById.get(p.getUserId());
                    return new ParticipantDto(
                            p.getUserId(),
                            u == null ? null : u.getCfHandle(),
                            u == null ? null : u.getAvatarUrl(),
                            p.getTeam(),
                            p.getSlot(),
                            p.getStatus());
                })
                .toList();

        ProblemDto problem = (room.getProblemId() == null || STATUS_WAITING.equals(room.getStatus()))
                ? null
                : toProblemDto(room);

        return new RoomStateDto(
                room.getRoomCode(),
                room.getRoomType(),
                room.getStatus(),
                room.getHostId(),
                room.getProblemRating(),
                problem,
                participantDtos,
                room.getStartedAt());
    }

    /**
     * Builds a {@link ProblemDto} from the room's stored problem id (format
     * "{contestId}{index}", e.g. "1234A") and url. Contest id / index are parsed
     * best-effort from the problem key.
     */
    private ProblemDto toProblemDto(DuelRoom room) {
        String key = room.getProblemId();
        Integer contestId = null;
        String index = null;
        if (key != null) {
            int splitAt = 0;
            while (splitAt < key.length() && Character.isDigit(key.charAt(splitAt))) {
                splitAt++;
            }
            if (splitAt > 0) {
                try {
                    contestId = Integer.parseInt(key.substring(0, splitAt));
                } catch (NumberFormatException ignored) {
                    contestId = null;
                }
            }
            index = splitAt < key.length() ? key.substring(splitAt) : null;
        }
        return new ProblemDto(
                key,
                contestId,
                index,
                null, // name not persisted on the room in Phase 2
                room.getProblemUrl(),
                room.getProblemRating());
    }
}
