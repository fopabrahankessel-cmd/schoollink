-- Task 3.14 (SPEC §11): two fictional schools for the load test, generated data only (CLAUDE.md:
-- no real student data). Each school: 4 blocks of 10 classes, 8 periods a day, one teacher per
-- class teaching all 8 periods Monday to Friday, 50 students per class, one guardian per student.
-- Even-numbered guardians use the app (an account and a fake device token, so they get a push);
-- odd-numbered ones are SMS only (their SMS waits for the digest, BR-22).
-- Staff sign in with their e-mail and the dev password "schoollink-dev".
-- Run once on an empty load database, after the backend has run the migrations (Flyway).

DO $$
DECLARE
    pw constant text := '$2a$10$pmq5zchQLRRyTL4q5NLcN.amvOTbjmUjlEkmGO9CaVODvjNfcPIb.';
    firsts constant text[] := ARRAY['Amina', 'Boris', 'Carine', 'Didier', 'Estelle', 'Fabrice', 'Grace',
        'Hugo', 'Inès', 'Jules', 'Kevin', 'Laure', 'Marc', 'Nadia', 'Olivier', 'Prisca', 'Quentin',
        'Rose', 'Serge', 'Tatiana'];
    lasts constant text[] := ARRAY['Abena', 'Bella', 'Chouala', 'Djeumo', 'Essomba', 'Fouda', 'Gwet',
        'Hamadou', 'Ibrahim', 'Jengu', 'Kamga', 'Lobe', 'Mbarga', 'Ngono', 'Owona', 'Pouth', 'Simo',
        'Tchana', 'Wamba', 'Yomba'];
    s int; b int; c int; n int; p int; d int;
    v_school_id uuid; v_year_id uuid; v_block_id uuid; v_class_id uuid; v_teacher_id uuid;
    v_student_id uuid; v_guardian_id uuid; v_parent_id uuid; v_staff_id uuid; v_period_id uuid;
    periods uuid[];
    class_no int;
    phone text;
BEGIN
    IF EXISTS (SELECT 1 FROM school WHERE name LIKE 'Load School %') THEN
        RAISE EXCEPTION 'the load schools are already seeded';
    END IF;

    FOR s IN 1..2 LOOP
        INSERT INTO school (name, language)
        VALUES ('Load School ' || s || ' (fictional)', CASE s WHEN 1 THEN 'FR' ELSE 'EN' END)
        RETURNING id INTO v_school_id;

        INSERT INTO academic_year (school_id, label, start_date, end_date)
        VALUES (v_school_id, '2026-2027', '2026-09-07', '2027-07-02') RETURNING id INTO v_year_id;
        INSERT INTO term (school_id, academic_year_id, label, start_date, end_date)
        VALUES (v_school_id, v_year_id, 'Term 1', '2026-09-07', '2026-12-18');

        periods := ARRAY[]::uuid[];
        FOR p IN 1..8 LOOP
            INSERT INTO period (school_id, number, start_time, end_time)
            VALUES (v_school_id, p, time '07:30' + (p - 1) * interval '55 minutes', time '07:30' + p * interval '55 minutes')
            RETURNING id INTO v_period_id;
            periods := periods || v_period_id;
        END LOOP;

        FOR b IN 1..4 LOOP
            INSERT INTO block (school_id, name) VALUES (v_school_id, 'Block ' || b) RETURNING id INTO v_block_id;

            -- one DM and one assistant DM per block (BR-10 scope)
            INSERT INTO app_user (school_id, first_name, last_name, email, password_hash)
            VALUES (v_school_id, 'DM', 'S' || s || 'B' || b, format('dm-s%s-b%s@load.test', s, b), pw)
            RETURNING id INTO v_staff_id;
            INSERT INTO role_assignment (school_id, user_id, role, block_id) VALUES (v_school_id, v_staff_id, 'DM', v_block_id);
            INSERT INTO app_user (school_id, first_name, last_name, email, password_hash)
            VALUES (v_school_id, 'Gate', 'S' || s || 'B' || b, format('gate-s%s-b%s@load.test', s, b), pw)
            RETURNING id INTO v_staff_id;
            INSERT INTO role_assignment (school_id, user_id, role, block_id)
            VALUES (v_school_id, v_staff_id, 'ASSISTANT_DM', v_block_id);

            FOR c IN 1..10 LOOP
                class_no := (b - 1) * 10 + c;
                INSERT INTO school_class (school_id, academic_year_id, block_id, name, level)
                VALUES (v_school_id, v_year_id, v_block_id, format('Class %s', lpad(class_no::text, 2, '0')), 'Level ' || b)
                RETURNING id INTO v_class_id;

                INSERT INTO app_user (school_id, first_name, last_name, email, password_hash)
                VALUES (v_school_id, 'Teacher', format('S%sC%s', s, lpad(class_no::text, 2, '0')),
                        format('teacher-s%s-c%s@load.test', s, lpad(class_no::text, 2, '0')), pw)
                RETURNING id INTO v_teacher_id;
                INSERT INTO role_assignment (school_id, user_id, role) VALUES (v_school_id, v_teacher_id, 'TEACHER');
                INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject)
                VALUES (v_school_id, v_teacher_id, v_class_id, 'Mathematics');
                FOR d IN 1..5 LOOP
                    FOR p IN 1..8 LOOP
                        INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
                        VALUES (v_school_id, v_class_id, d, periods[p], v_teacher_id, 'Mathematics');
                    END LOOP;
                END LOOP;

                FOR n IN 1..50 LOOP
                    v_student_id := gen_random_uuid();
                    v_guardian_id := gen_random_uuid();
                    -- fake numbers: +237 69 <school> <6 digits>
                    phone := format('+23769%s%s', s, lpad((class_no * 100 + n)::text, 6, '0'));
                    INSERT INTO student (id, school_id, class_id, matricule, first_name, last_name, sex, birth_year)
                    VALUES (v_student_id, v_school_id, v_class_id, format('L%s-%s-%s', s, lpad(class_no::text, 2, '0'), lpad(n::text, 2, '0')),
                            firsts[1 + (n - 1) % 20], lasts[1 + (class_no + n) % 20],
                            CASE n % 2 WHEN 0 THEN 'F' ELSE 'M' END, 2010 + n % 5);
                    INSERT INTO guardian (id, school_id, name, phone, language)
                    VALUES (v_guardian_id, v_school_id, 'Parent ' || lasts[1 + (class_no + n) % 20], phone,
                            CASE s WHEN 1 THEN 'FR' ELSE 'EN' END);
                    INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
                    VALUES (v_school_id, v_student_id, v_guardian_id, 'GUARDIAN', true);

                    IF n % 2 = 0 THEN
                        INSERT INTO app_user (school_id, first_name, last_name, phone, language)
                        VALUES (v_school_id, 'Parent', lasts[1 + (class_no + n) % 20], phone,
                                CASE s WHEN 1 THEN 'FR' ELSE 'EN' END)
                        RETURNING id INTO v_parent_id;
                        INSERT INTO role_assignment (school_id, user_id, role) VALUES (v_school_id, v_parent_id, 'PARENT');
                        UPDATE guardian SET user_id = v_parent_id WHERE id = v_guardian_id;
                        INSERT INTO device_token (school_id, user_id, token, platform)
                        VALUES (v_school_id, v_parent_id, 'load-' || v_parent_id, 'ANDROID');
                    END IF;
                END LOOP;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

SELECT s.name,
       (SELECT count(*) FROM school_class c WHERE c.school_id = s.id) AS classes,
       (SELECT count(*) FROM period p WHERE p.school_id = s.id) AS periods,
       (SELECT count(*) FROM student st WHERE st.school_id = s.id) AS students,
       (SELECT count(*) FROM guardian g WHERE g.school_id = s.id AND g.user_id IS NOT NULL) AS app_guardians
  FROM school s WHERE s.name LIKE 'Load School %' ORDER BY s.name;
