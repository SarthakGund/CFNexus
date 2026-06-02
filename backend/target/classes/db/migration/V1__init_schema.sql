-- CFNexus Phase 1 — initial schema (spec §5)
-- gen_random_uuid() requires the pgcrypto extension.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cf_handle       VARCHAR(50) UNIQUE NOT NULL,
    cf_user_id      BIGINT UNIQUE NOT NULL,
    cf_rating       INT DEFAULT 0,
    cf_max_rating   INT DEFAULT 0,
    cf_rank         VARCHAR(30),
    cf_max_rank     VARCHAR(30),
    avatar_url      TEXT,
    duel_rating     INT DEFAULT 1200,
    duel_wins       INT DEFAULT 0,
    duel_losses     INT DEFAULT 0,
    duel_draws      INT DEFAULT 0,
    unrated_wins    INT DEFAULT 0,
    current_streak  INT DEFAULT 0,
    max_streak      INT DEFAULT 0,
    fastest_solve_ms BIGINT,
    bio             TEXT,
    favorite_lang   VARCHAR(20),
    is_online       BOOLEAN DEFAULT FALSE,
    last_seen       TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_cf_handle ON users (cf_handle);
CREATE INDEX idx_users_cf_handle_lower ON users (LOWER(cf_handle));
CREATE INDEX idx_users_duel_rating ON users (duel_rating DESC);
CREATE INDEX idx_users_unrated_wins ON users (unrated_wins DESC);
CREATE INDEX idx_users_current_streak ON users (current_streak DESC);
CREATE INDEX idx_users_fastest_solve ON users (fastest_solve_ms ASC);

-- ---------------------------------------------------------------------------
-- duel_rooms
-- ---------------------------------------------------------------------------
CREATE TABLE duel_rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code       VARCHAR(12) UNIQUE NOT NULL,
    room_type       VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    host_id         UUID REFERENCES users(id),
    problem_rating  INT NOT NULL,
    problem_id      VARCHAR(30),
    problem_url     TEXT,
    winner_team     INT,
    started_at      TIMESTAMP,
    ended_at        TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_duel_rooms_room_code ON duel_rooms (room_code);
CREATE INDEX idx_duel_rooms_status ON duel_rooms (status);
CREATE INDEX idx_duel_rooms_host_id ON duel_rooms (host_id);

-- ---------------------------------------------------------------------------
-- duel_participants
-- ---------------------------------------------------------------------------
CREATE TABLE duel_participants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID REFERENCES duel_rooms(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id),
    team        INT,
    slot        INT,
    status      VARCHAR(20) DEFAULT 'ACTIVE',
    joined_at   TIMESTAMP DEFAULT NOW(),
    UNIQUE(room_id, user_id)
);

CREATE INDEX idx_duel_participants_room_id ON duel_participants (room_id);
CREATE INDEX idx_duel_participants_user_id ON duel_participants (user_id);

-- ---------------------------------------------------------------------------
-- duel_results
-- ---------------------------------------------------------------------------
CREATE TABLE duel_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id             UUID REFERENCES duel_rooms(id),
    winner_id           UUID REFERENCES users(id),
    loser_id            UUID REFERENCES users(id),
    result_type         VARCHAR(20) NOT NULL,
    winner_rating_before INT,
    winner_rating_after  INT,
    loser_rating_before  INT,
    loser_rating_after   INT,
    rating_delta        INT,
    solve_duration_ms   BIGINT,
    problem_id          VARCHAR(30),
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_duel_results_room_id ON duel_results (room_id);
CREATE INDEX idx_duel_results_winner_id ON duel_results (winner_id);
CREATE INDEX idx_duel_results_loser_id ON duel_results (loser_id);

-- ---------------------------------------------------------------------------
-- match_history
-- ---------------------------------------------------------------------------
CREATE TABLE match_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id         UUID REFERENCES duel_rooms(id),
    user_id         UUID REFERENCES users(id),
    opponent_ids    UUID[],
    result          VARCHAR(20),
    problem_id      VARCHAR(30),
    problem_rating  INT,
    problem_url     TEXT,
    duel_type       VARCHAR(20),
    duration_ms     BIGINT,
    rating_before   INT,
    rating_after    INT,
    played_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_match_history_user_id ON match_history (user_id);
CREATE INDEX idx_match_history_room_id ON match_history (room_id);
CREATE INDEX idx_match_history_played_at ON match_history (played_at DESC);

-- ---------------------------------------------------------------------------
-- friends
-- ---------------------------------------------------------------------------
CREATE TABLE friends (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID REFERENCES users(id),
    addressee_id UUID REFERENCES users(id),
    status      VARCHAR(20) DEFAULT 'PENDING',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(requester_id, addressee_id)
);

CREATE INDEX idx_friends_requester_id ON friends (requester_id);
CREATE INDEX idx_friends_addressee_id ON friends (addressee_id);
CREATE INDEX idx_friends_status ON friends (status);

-- ---------------------------------------------------------------------------
-- achievements
-- ---------------------------------------------------------------------------
CREATE TABLE achievements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    icon        VARCHAR(50),
    condition_type VARCHAR(30),
    condition_value INT
);

CREATE INDEX idx_achievements_code ON achievements (code);

-- ---------------------------------------------------------------------------
-- user_achievements
-- ---------------------------------------------------------------------------
CREATE TABLE user_achievements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),
    achievement_id  UUID REFERENCES achievements(id),
    earned_at       TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, achievement_id)
);

CREATE INDEX idx_user_achievements_user_id ON user_achievements (user_id);
CREATE INDEX idx_user_achievements_achievement_id ON user_achievements (achievement_id);

-- ---------------------------------------------------------------------------
-- rating_history
-- ---------------------------------------------------------------------------
CREATE TABLE rating_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id),
    duel_id     UUID REFERENCES duel_rooms(id),
    rating      INT NOT NULL,
    delta       INT NOT NULL,
    recorded_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_rating_history_user_id ON rating_history (user_id);
CREATE INDEX idx_rating_history_recorded_at ON rating_history (recorded_at DESC);

-- ---------------------------------------------------------------------------
-- chat_keys (E2E encryption)
-- ---------------------------------------------------------------------------
CREATE TABLE chat_keys (
    room_id         UUID REFERENCES duel_rooms(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id),
    public_key_b64  TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY(room_id, user_id)
);

CREATE INDEX idx_chat_keys_user_id ON chat_keys (user_id);
