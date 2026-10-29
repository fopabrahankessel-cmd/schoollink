# Import samples for the dev seed (UC-A3)

Made-up students for a manual check of the import screen against **Lycée Démo A (fictif)** (dev profile, signed in as `admin@lycee-demo-a.test`). No real person is in these files.

- `demo-a-with-errors.csv`: every row but the first has errors (empty name, unknown sex and class, a matricule already in the seed and one repeated in the file, a foreign phone, an implausible birth year, both guardians with the same phone). Nothing can be committed.
- `demo-a-clean.csv`: five valid students. Two are siblings and share one guardian. Commit it once; a second upload is refused because the matricules then exist. Reset with `docker compose down -v`, then restart the backend.
