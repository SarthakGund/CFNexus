-- CFNexus Phase 1 — seed achievements (spec §13, 17 rows)
-- Idempotent: re-running does nothing thanks to ON CONFLICT (code).
--
-- condition_type / condition_value are derived from each textual condition so
-- the AchievementChecker (Phase 6) can evaluate them generically:
--   WINS          -> total rated wins >= value
--   STREAK        -> current/max win streak >= value
--   RATING        -> duel_rating >= value
--   FAST_SOLVE_MS -> a solve faster than value milliseconds
--   CLEAN_STREAK  -> value consecutive wins with no draw/resign
--   HARD_PROBLEM  -> solved problem rated value+ above own CF rating
--   TEAM_WIN      -> win a team duel with value-per-side players

INSERT INTO achievements (code, name, description, icon, condition_type, condition_value) VALUES
    ('FIRST_WIN',    'First Blood',        'Win your first duel',                              'sword',      'WINS',          1),
    ('STREAK_5',     'On Fire',            '5-win streak',                                     'flame',      'STREAK',        5),
    ('STREAK_10',    'Unstoppable',        '10-win streak',                                    'flame-2',    'STREAK',        10),
    ('STREAK_20',    'Legendary',          '20-win streak',                                    'crown',      'STREAK',        20),
    ('RANK_UP_1',    'Rising Star',        'Reach 1400 duel rating',                           'star',       'RATING',        1400),
    ('RANK_UP_2',    'Expert Duelist',     'Reach 1600 duel rating',                           'star-2',     'RATING',        1600),
    ('RANK_UP_3',    'Master Duelist',     'Reach 1900 duel rating',                           'medal',      'RATING',        1900),
    ('GRANDMASTER',  'Grandmaster',        'Reach 2300 duel rating',                           'trophy',     'RATING',        2300),
    ('FAST_10',      'Speed Demon',        'Solve a problem in under 10 minutes',              'timer',      'FAST_SOLVE_MS', 600000),
    ('FAST_5',       'Lightning',          'Solve a problem in under 5 minutes',               'bolt',       'FAST_SOLVE_MS', 300000),
    ('FAST_1',       'One-Minute Wonder',  'Solve a problem in under 1 minute',                'zap',        'FAST_SOLVE_MS', 60000),
    ('WINS_10',      'Battle Tested',      'Win 10 rated duels',                               'shield',     'WINS',          10),
    ('WINS_50',      'Veteran',            'Win 50 rated duels',                               'shield-2',   'WINS',          50),
    ('WINS_100',     'Century',            'Win 100 rated duels',                              'shield-3',   'WINS',          100),
    ('HARD_PROBLEM', 'Slayer',             'Solve a problem rated 200+ above your CF rating',  'skull',      'HARD_PROBLEM',  200),
    ('CLEAN_WIN',    'Flawless',           'Win 10 duels in a row without a draw or resign',   'sparkles',   'CLEAN_STREAK',  10),
    ('TEAM_WIN_4V4', 'Squad Goals',        'Win a 4v4 team duel',                              'users',      'TEAM_WIN',      4)
ON CONFLICT (code) DO NOTHING;
