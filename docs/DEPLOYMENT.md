# Deploying SchoolLink for the pilot

This guide takes SchoolLink from your laptop to a real server that the two pilot schools use every day. It assumes you have never deployed anything. Follow the parts in order. Every command is given in full, and each part ends with a check so you know it worked before moving on.

Prices were checked in November 2026. They change, so confirm them on each provider's site before you pay.

## Contents

1. [What you will end up with](#1-what-you-will-end-up-with)
2. [What it costs](#2-what-it-costs)
3. [Timeline: start the slow things first](#3-timeline-start-the-slow-things-first)
4. [Accounts to open](#4-accounts-to-open)
5. [Buy a domain name](#5-buy-a-domain-name)
6. [Rent the server](#6-rent-the-server)
7. [Point the domain at the server](#7-point-the-domain-at-the-server)
8. [Prepare the server](#8-prepare-the-server)
9. [Set up Twilio for SMS](#9-set-up-twilio-for-sms)
10. [Put SchoolLink on the server](#10-put-schoollink-on-the-server)
11. [Create the two schools](#11-create-the-two-schools)
12. [Build the Android app for production](#12-build-the-android-app-for-production)
13. [Get the app onto phones](#13-get-the-app-onto-phones)
14. [Backups](#14-backups)
15. [Monitoring](#15-monitoring)
16. [Installing a new version](#16-installing-a-new-version)
17. [Everyday commands and troubleshooting](#17-everyday-commands-and-troubleshooting)
18. [Go-live checklist](#18-go-live-checklist)

---

## 1. What you will end up with

```
   Phones (teachers, DMs, parents)        Browsers (admin, DM, researcher)
              │                                       │
              └──────────── HTTPS (port 443) ─────────┘
                                 │
                 https://schoollink.your-domain.org
                                 │
   ┌──────────────── one rented server (Ubuntu Linux) ───────────────┐
   │                                                                  │
   │  caddy      HTTPS certificate (automatic), the web dashboard,   │
   │             forwards /api to the backend                         │
   │     │                                                            │
   │  backend    the Spring Boot API (Java 21)  ──► Firebase (push)  │
   │     │                                       ──► Twilio (SMS)    │
   │  db         PostgreSQL 16, its data on the server's disk        │
   │                                                                  │
   │  backup.sh  every evening: a copy of the database               │
   └──────────────────────────────────────────────────────────────────┘
```

- **One server runs everything.** Docker keeps each part in its own "container". One command, `docker compose up -d`, starts all three containers, and they restart by themselves after a crash or a reboot.
- **Caddy** is the front door. It gets a free HTTPS certificate from Let's Encrypt and renews it before it expires. It also serves the web dashboard and passes everything under `/api` to the backend.
- **The database and the API are never reachable from the internet.** Only Caddy, on ports 80 and 443, is.
- **The files for all of this are in the repository**:

  | File | What it does |
  | --- | --- |
  | `deploy/docker-compose.yml` | Describes the three containers and how they connect |
  | `deploy/Caddyfile` | Caddy's settings: the domain, HTTPS, where each address goes |
  | `deploy/.env.example` | The list of settings and secrets you fill in on the server |
  | `deploy/backup.sh` | The nightly database backup |
  | `backend/Dockerfile` | How the backend is built into a container |
  | `web/Dockerfile` | How the web dashboard is built and served by Caddy |

All of this was tested on a local machine, from building to the first sign-in, with a backup taken and restored.

> **Note on the SPEC.** SPEC §6 names Nginx and Let's Encrypt. Caddy does the same job, and it gets and renews the Let's Encrypt certificate by itself, which removes the certbot setup and the renewal cron that beginners most often get wrong.

---

## 2. What it costs

Approximate exchange rates: 1 € = 656 FCFA (fixed rate), 1 US$ ≈ 570 FCFA.

### One-time costs

| Item | Price | Notes |
| --- | --- | --- |
| Domain name (`.org`, `.com`) | about US$ 10–15 per year | Paid yearly. A `.cm` domain costs more and is sold by local registrars |
| Google Play developer account | US$ 25, once | Only if you publish on Google Play (part 13). A direct APK download is free |
| Activating parent accounts | about US$ 1.27 per French-speaking parent, US$ 0.63 per English-speaking parent | Each parent who installs the app receives two SMS: the invitation code and the verification code. See "Why French SMS cost more" below |

### Monthly costs

| Item | Price per month | Notes |
| --- | --- | --- |
| Server: Hetzner Cloud CX23 (2 vCPU, 4 GB RAM, 40 GB disk) | about € 4.50 (≈ 3,000 FCFA) | Includes the public IPv4 address. Hetzner raised prices twice in 2026, so check [hetzner.com/cloud](https://www.hetzner.com/cloud) |
| Hetzner automatic backups | + 20 % of the server price, about € 1 | Recommended. A full image of the server, 7 days kept |
| HTTPS certificate | free | Let's Encrypt, through Caddy |
| Push notifications (Firebase Cloud Messaging) | free | No limit that matters for two schools |
| Uptime monitor (UptimeRobot) | free | Part 15 |
| **SMS (Twilio)** | **US$ 0.317 per SMS segment to Cameroon** | **The biggest cost by far.** See below |

### SMS: the cost that matters

Everything except SMS costs less than US$ 10 a month. SMS can cost hundreds.

**Who receives an SMS:**
- A parent without the app gets **one SMS per child per day** with absences or lateness (the digest, BR-22).
- A parent with the app gets the same digest by SMS **only if they did not open the push** (D33).
- Summons always go by SMS as well (BR-23).
- Each parent who activates the app receives two SMS, once.

**The safety net (BR-25).** Each school has a monthly SMS limit, **2,000 messages by default**. When a school reaches it, only summons are still sent by SMS. The admin sees the counter on the dashboard. The default 2,000 caps the worst case at about **US$ 634 per school per month** (2,000 × 0.317). To change the limit, see part 17.

**An example per school per month** (1,500 students, about 4 % absent or late each day, 22 school days):

| Share of parents who use the app and open their pushes | Digest SMS per month | Cost per month |
| --- | --- | --- |
| 20 % | about 1,600 | about US$ 510 |
| 50 % | about 1,000 | about US$ 320 |
| 80 % | about 400 | about US$ 130 |

**Example total for a 3-month pilot in 2 schools**, with half the parents on the app and 300 parents per school activating the app:
- server: 4 months × about US$ 6 = **US$ 24**
- domain: **US$ 12**
- parent activations: 600 × US$ 1.27 = **US$ 760**
- digests: 2 schools × 3 months × US$ 320 = **US$ 1,920**
- **Total: about US$ 2,700 (≈ 1.5 million FCFA)**

**Ways to spend less:**
- **Get parents onto the app.** Every parent who uses the app and opens pushes costs almost nothing. Push adoption is the biggest saving.
- **Lower the monthly limit** to what you can afford (part 17). For example, 800 per school caps SMS at about US$ 250 per school per month.
- **Keep accents out of the school name in SchoolLink.** The digest SMS ends with the school name. See below.
- **Use a local SMS provider.** Cameroonian SMS aggregators usually charge far less than Twilio. Ask two or three for quotes. SchoolLink sends SMS through one interface (`SmsProvider`), so adding another provider is a small backend change. It is a code change, though, not a setting.

**Why French SMS cost more.** One SMS segment holds 160 plain characters. As soon as a message contains a single accented character (é, è, à…), the whole message switches to a format that holds only 70 characters per segment, so the same text can cost two segments instead of one. The French invitation and verification messages contain accents, so they cost two segments each. A digest whose student or school name has an accent can also cost two.

---

## 3. Timeline: start the slow things first

Some steps take days or weeks and you cannot speed them up. Start them on day 1, in parallel with everything else.

| When | What | Why it is slow |
| --- | --- | --- |
| Day 1 | Ask the head teachers for their written authorisation, and prepare the parent information notice and consent form (SPEC §11) | Needs signatures and meetings |
| Day 1 | Open the Twilio account and **register the "SchoolLink" sender ID for Cameroon** (part 9) | Operators must approve it. It can take several weeks, and MTN does not deliver without it |
| Day 1 | If you want Google Play: open the developer account (part 13) | Identity checks take days. A new personal account must then run a 14-day closed test with at least 12 testers before it can publish publicly |
| Week 1 | Domain, server, installation (parts 5–10) | About one day of work |
| Week 1–2 | Create the schools, import students, set the timetable (part 11) | Depends on getting class lists from the schools |
| Week 2–3 | Train staff, then invite parents | Part 18 |

---

## 4. Accounts to open

Use one email address that you will keep for years, ideally a project address rather than a personal one. Turn on two-factor authentication everywhere.

| Account | Used for | Cost |
| --- | --- | --- |
| Domain registrar (Namecheap, Porkbun or Cloudflare) | The domain name | ~US$ 12/year |
| [Hetzner Cloud](https://www.hetzner.com/cloud) | The server | ~€ 5.50/month |
| [Twilio](https://www.twilio.com) | Sending SMS | Pay per SMS, prepaid |
| [Firebase](https://console.firebase.google.com) | Push notifications | Free; the project `schoollink-7fbb4` already exists |
| [UptimeRobot](https://uptimerobot.com) | Alerts when the server is down | Free |
| [Google Play Console](https://play.google.com/console) (optional) | Publishing the app | US$ 25 once |

You need a bank card that works for international online payments. Some prepaid or virtual cards are refused by Hetzner or Twilio, so try a small payment early.

---

## 5. Buy a domain name

A domain is the address people type, for example `schoollink-pilot.org`. Users open the dashboard at a subdomain of it, for example `https://app.schoollink-pilot.org`. The app has this address built in, so **choose it carefully: changing it later means rebuilding and reinstalling the app**.

1. On the registrar's site, search for a name and buy it for 1 or 2 years. Turn on automatic renewal: if the domain expires, everything stops working.
2. Decline the extras (hosting, email, "SSL"). Caddy provides HTTPS for free.
3. Choose the full address SchoolLink will use, e.g. `app.schoollink-pilot.org`. This guide calls it **YOUR_DOMAIN**.

✔ **Check:** the domain appears in your registrar's dashboard with "DNS" or "Advanced DNS" settings.

---

## 6. Rent the server

### 6.1 Create an SSH key on your Windows PC

An SSH key is how you prove to the server that you are you, instead of a password. Do this once, in PowerShell:

```powershell
ssh-keygen -t ed25519 -C "schoollink-server"
```

Press Enter to accept the location, then type a passphrase you will remember. This creates two files in `C:\Users\fopab\.ssh\`:
- `id_ed25519`: your **private** key. Never share it and never copy it anywhere else.
- `id_ed25519.pub`: your **public** key. This one goes to the server.

Show the public key so you can copy it:

```powershell
Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub
```

### 6.2 Create the server

1. In the Hetzner Cloud console, create a project called `SchoolLink`, then **Add Server**:
   - **Location:** Nuremberg or Falkenstein (Germany). Hetzner has no data centre in Africa. From Cameroon, Germany and Finland are similar, and SchoolLink sends little data, so the distance does not matter.
   - **Image:** Ubuntu 24.04
   - **Type:** Shared vCPU, x86, **CX23** (2 vCPU, 4 GB RAM, 40 GB)
   - **Networking:** keep Public IPv4 and IPv6 on
   - **SSH keys:** Add SSH key, then paste the line from `id_ed25519.pub`
   - **Backups:** turn on
   - **Name:** `schoollink-pilot`
2. Click **Create & Buy now**. After a minute the server shows its **IPv4 address**, e.g. `203.0.113.25`. This guide calls it **SERVER_IP**.

✔ **Check:** in PowerShell, `ssh root@SERVER_IP` asks for your key's passphrase, then shows a prompt like `root@schoollink-pilot:~#`. Type `exit` to leave.

> **Data protection.** The data of minors will live on a server in Germany. Check with your supervisors that this is acceptable under Cameroon's Law No. 2024/017 (SPEC §11). If it is not, choose a host in Cameroon. Everything in this guide works on any Ubuntu server.

---

## 7. Point the domain at the server

In your registrar's DNS settings, add one record:

| Type | Host / Name | Value | TTL |
| --- | --- | --- | --- |
| A | `app` (the part before your domain; `@` for the bare domain) | SERVER_IP | Automatic or 300 |

Changes take from a few minutes to a few hours to spread.

✔ **Check:** in PowerShell, `Resolve-DnsName YOUR_DOMAIN` shows SERVER_IP. Do not continue until it does: Caddy can only get the HTTPS certificate once the domain points to the server.

---

## 8. Prepare the server

Connect as `root` with `ssh root@SERVER_IP`. Run each block, and read the output for errors before running the next one.

**Update the system and set the clock to Cameroon time** (cron times below are then local time):

```bash
apt update && apt -y upgrade
timedatectl set-timezone Africa/Douala
apt install -y unattended-upgrades   # security updates install by themselves
```

**Create your own user** instead of working as `root`:

```bash
adduser schoollink                     # choose a strong password; the other questions can stay empty
usermod -aG sudo schoollink
mkdir -p /home/schoollink/.ssh
cp ~/.ssh/authorized_keys /home/schoollink/.ssh/
chown -R schoollink:schoollink /home/schoollink/.ssh
```

**Firewall**: allow only SSH and the web:

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw enable                             # answer y
```

**Swap**: extra memory on disk, so building the containers never runs out of RAM:

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

**Docker**:

```bash
curl -fsSL https://get.docker.com | sh
usermod -aG docker schoollink
```

**Turn off password logins**, so only your SSH key can connect:

```bash
sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh
```

Now leave (`exit`) and connect as your user from now on: `ssh schoollink@SERVER_IP`.

✔ **Check:** as `schoollink`, `docker run --rm hello-world` prints "Hello from Docker!".

---

## 9. Set up Twilio for SMS

The server refuses to start without a Twilio account (D32), so do this before part 10.

1. **Sign up** at twilio.com and verify your email and phone number.
2. **Upgrade the account** (Console → Admin → Account billing → Upgrade) and add credit, for example US$ 20 to start. A trial account only sends to numbers you have verified yourself and adds "Sent from a Twilio trial account" to every SMS.
3. **Allow Cameroon.** In Console → Messaging → Settings → **Geo permissions**, tick **Cameroon**. Without it, every SMS is refused.
4. **Register the sender ID "SchoolLink"** (11 characters at most) through Console → **Trust Hub** → alphanumeric sender ID registration, for Cameroon. MTN delivers only from a registered sender ID. Start this on day 1: approval takes time.
5. **Copy your credentials.** On the Console home page, note the **Account SID** (starts with `AC`) and the **Auth Token** (click to show). The token is a password: never put it in the repository, an email or a chat.
6. **Set spending alerts.** In Console → Monitor → Usage → **Triggers**, add a trigger that emails you when SMS spending reaches, say, US$ 100, and another at US$ 300.

✔ **Check:** the account shows as upgraded, Cameroon is ticked, and the sender ID registration is submitted (it may still be pending).

---

## 10. Put SchoolLink on the server

All commands in this part run on the server, as `schoollink`.

### 10.1 Get the code

```bash
sudo mkdir -p /opt/schoollink && sudo chown schoollink:schoollink /opt/schoollink
git clone https://github.com/fopabrahankessel-cmd/schoollink.git /opt/schoollink
cd /opt/schoollink/deploy
```

If the repository is private, git asks for a username and password. Use your GitHub username and, as the password, a **fine-grained personal access token** (GitHub → Settings → Developer settings → Personal access tokens), limited to this repository with **read-only "Contents"** access.

### 10.2 Copy the Firebase key from your PC

On the server, create the folder the key goes in:

```bash
mkdir -p /opt/schoollink/deploy/secrets && chmod 700 /opt/schoollink/deploy/secrets
```

On **your PC**, in PowerShell, copy the file over:

```powershell
scp C:\Users\fopab\secrets\firebase-service-account.json schoollink@SERVER_IP:/opt/schoollink/deploy/secrets/
```

Back on the server, make the file readable by the backend container:

```bash
chmod 644 /opt/schoollink/deploy/secrets/firebase-service-account.json
```

Only you can open the `secrets` folder, because of the `chmod 700`. The backend container runs as its own user, so the file itself must stay readable (644).

### 10.3 Fill in the settings

```bash
cp .env.example .env
openssl rand -hex 24        # copy the result: it is DB_PASSWORD
openssl rand -base64 48     # copy the result: it is JWT_SECRET
nano .env
```

`nano` is a simple text editor: type your changes, save with **Ctrl+O** then Enter, and quit with **Ctrl+X**.

| Setting | What to put |
| --- | --- |
| `SCHOOLLINK_DOMAIN` | YOUR_DOMAIN, without `https://` |
| `DB_PASSWORD` | the first random value |
| `JWT_SECRET` | the second random value |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` | from part 9 |
| `TWILIO_FROM` | `SchoolLink` (the registered sender ID) |
| `BOOTSTRAP_…` | leave empty for now (part 11) |

**Keep a copy of `.env` somewhere safe off the server**, such as a password manager. Never commit it: git ignores it. If you lose `DB_PASSWORD`, you can no longer open the database. If you change `JWT_SECRET`, everybody is signed out.

### 10.4 Start

```bash
docker compose up -d --build
```

The first build takes 5 to 15 minutes, because it downloads Java, Node and their libraries. Later starts take seconds.

✔ **Check:**
- `docker compose ps` shows `db` (healthy), `backend` and `caddy`, all `Up`.
- `docker compose logs backend | grep Started` shows `Started SchoolLinkApplication`.
- In a browser, `https://YOUR_DOMAIN` shows the SchoolLink sign-in page with a padlock.
- `https://YOUR_DOMAIN/actuator/health` shows `{"status":"UP",…}`.

If the page does not load, see part 17.

---

## 11. Create the two schools

SchoolLink creates a school and its first administrator when it starts, from the `BOOTSTRAP_…` settings. It does this once: a school whose name already exists is left alone.

1. On the server, run `nano .env` and fill in the first school:
   ```
   BOOTSTRAP_SCHOOL_NAME=Lycee Bilingue de Exemple
   BOOTSTRAP_SCHOOL_LANGUAGE=FR
   BOOTSTRAP_ADMIN_EMAIL=admin.lycee1@your-domain.org
   BOOTSTRAP_ADMIN_PASSWORD=a-temporary-password-of-12+-characters
   ```
   The school name appears at the end of every SMS digest, so keep it short and **without accents** (part 2).
2. Restart the backend with the new settings, then check the log:
   ```bash
   docker compose up -d backend
   docker compose logs backend | grep Bootstrap
   ```
   It should say `Bootstrap: created the school '…' and its administrator …`.
3. Repeat steps 1 and 2 with the second school's name and administrator.
4. **Empty the four `BOOTSTRAP_` lines** in `.env`, so the temporary password does not stay in the file, and run `docker compose up -d backend` again.

Then, for each school, its administrator signs in at `https://YOUR_DOMAIN`. They must choose a new password at the first sign-in. Then they follow the setup checklist on the home page, in this order:
1. Calendar: the school year, terms and holidays.
2. Periods.
3. Blocks and classes.
4. Staff accounts: DMs, assistant DMs, teachers. A temporary password is shown once for each person: give it to them in person. They change it at their first sign-in.
5. Timetable.
6. Student and guardian import: see [IMPORT.md](IMPORT.md). Use the school's real list here, and **never commit it** to the repository.
7. Letterhead: the logo and header for the full-day absence report.
8. School start time, SMS digest times and alert thresholds: a DM sets these on the web dashboard (UC-D7).

The README's "Phase 1 walkthrough" shows every one of these screens with example values.

✔ **Check:** sign in on a phone as one of the new teachers, take a roll call in a test class, and see the record on the dashboard. Then cancel it as the DM.

---

## 12. Build the Android app for production

The app has the server's address built in. The app you use for testing talks to your laptop, so you build a separate production version.

### 12.1 Create the upload key (once, ever)

This key signs every version of the app you will ever publish. **If you lose it, you can never update the app on phones that installed it**, so back it up in two places, for example a USB key and a password manager.

In PowerShell:

```powershell
mkdir $env:USERPROFILE\secrets\android -Force
& "C:\Program Files\Java\jdk-21.0.13.0.3\bin\keytool.exe" -genkeypair -v `
  -keystore $env:USERPROFILE\secrets\android\schoollink-upload.jks `
  -storetype JKS -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

It asks for a password, which you'll need for every build. Then it asks your name, organisation and country; you can enter `SchoolLink` and `CM`.

Then create the file `mobile\android\key.properties` (git ignores it, so it is never committed):

```properties
storePassword=the password you chose
keyPassword=the password you chose
keyAlias=upload
storeFile=C:/Users/fopab/secrets/android/schoollink-upload.jks
```

Release builds now sign with this key. Without the file, they fall back to the debug key, which is fine for testing but rejected by Google Play.

### 12.2 Build

Before every new version, raise the version in `mobile/pubspec.yaml`: `version: 1.0.0+1` becomes `1.0.1+2`. The number after `+` must always go up, or phones and Google Play refuse the update.

```powershell
cd C:\Users\fopab\StudioProjects\mobile
$defines = "--dart-define=API_BASE_URL=https://YOUR_DOMAIN/api/v1"

# for Google Play
flutter build appbundle --release @defines

# for direct download: one file per processor type, each under 30 MB (NFR-4)
flutter build apk --release --split-per-abi @defines
```

Push needs no flag: the build reads the Firebase project from `mobile\android\app\google-services.json`, which must be on the machine that builds (it is never committed). The production server address, on the other hand, must always be given with `API_BASE_URL`.

The results:
- `build\app\outputs\bundle\release\app-release.aab`: upload this one to Google Play.
- `build\app\outputs\flutter-apk\app-arm64-v8a-release.apk`: most phones since about 2019.
- `build\app\outputs\flutter-apk\app-armeabi-v7a-release.apk`: older and entry-level phones (Android Go).

✔ **Check:** install the arm64 APK on your phone. The app reaches the production server, you can sign in as a teacher, and with a parent account you receive a push.

---

## 13. Get the app onto phones

### Option A: direct download (free, available the same day)

Recommended to start the pilot.

1. On the server, run `mkdir -p /opt/schoollink/deploy/downloads`.
2. From your PC, copy the two APKs over with clear names:
   ```powershell
   scp build\app\outputs\flutter-apk\app-arm64-v8a-release.apk schoollink@SERVER_IP:/opt/schoollink/deploy/downloads/schoollink.apk
   scp build\app\outputs\flutter-apk\app-armeabi-v7a-release.apk schoollink@SERVER_IP:/opt/schoollink/deploy/downloads/schoollink-ancien-telephone.apk
   ```
3. Share `https://YOUR_DOMAIN/download/schoollink.apk`, for example as a QR code printed on the parent information notice.

On the phone, Android asks to allow installing apps from the browser ("Install unknown apps"). Explain this step in the training and on the notice. If `schoollink.apk` says "App not installed", the phone is older: use the second file instead.

**The downside:** phones don't update by themselves. For each new version, people must download and install again.

### Option B: Google Play (automatic updates, takes weeks to set up)

1. Create the developer account (US$ 25) at play.google.com/console and complete the identity checks.
2. Create the app "SchoolLink". Fill in the store listing (description in French and English, screenshots, icon), the **privacy policy** (a web page explaining what is collected; reuse the parent information notice), the **Data safety form** and the content rating.
3. Upload the `.aab` to a **closed testing** track and invite at least 12 testers, for example colleagues and pilot teachers, by their Google account email. They must stay opted in for **14 days in a row**. This is required for new personal accounts.
4. Then apply for production access and publish.

You can also run the whole pilot on the closed testing track: invite the pilot staff and parents as testers, and they get the app and its updates through Play without the app ever being public.

---

## 14. Backups

The database holds every attendance record. If the server's disk fails without a backup, the pilot's data is gone. NFR-13 requires a daily backup and a restore tested at least once.

### 14.1 Nightly backup on the server

`deploy/backup.sh` saves the database to `deploy/backups/` and keeps 14 days. Make it run every evening at 21:15, after the nightly checks at 20:15:

```bash
cd /opt/schoollink/deploy
chmod +x backup.sh
./backup.sh                  # run it once by hand: it prints "backup written: …"
crontab -e                   # choose nano if asked, then add this line at the end:
```

```
15 21 * * * cd /opt/schoollink/deploy && ./backup.sh >> backups/backup.log 2>&1
```

### 14.2 A copy off the server

A backup on the same server doesn't help if the server is lost. Hetzner's automatic backups (part 6) cover the whole server. Also copy the database backups to your PC, for example every Friday:

```powershell
scp "schoollink@SERVER_IP:/opt/schoollink/deploy/backups/*.dump" D:\SchoolLink-backups\
```

Store these copies like the data they contain: on an encrypted disk, never on a shared drive, and deleted at the end of the retention period (SPEC §11: the school year plus one year).

### 14.3 Test a restore (do it once before go-live, then once a term)

This restores the latest backup into a separate test database. Nothing in the live database changes.

```bash
cd /opt/schoollink/deploy
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" restore_check'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d restore_check --no-owner' < "$(ls -t backups/*.dump | head -1)"
docker compose exec -T db psql -U schoollink -d restore_check -c "select name from school"
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" restore_check'
```

✔ **Check:** the third command lists your two schools. Note the date of the test: it is the evidence for NFR-13.

**A real restore** replaces the live data, so only do it after a disaster:

```bash
docker compose stop backend caddy
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' < backups/schoollink-YYYY-MM-DD.dump
docker compose start backend caddy
```

---

## 15. Monitoring

NFR-13 asks for 99 % availability during school hours (Mon–Fri, 07:00–18:00). You can only meet it if you learn about an outage quickly.

- **UptimeRobot (free):** add an HTTP(S) monitor on `https://YOUR_DOMAIN/actuator/health`, checked every 5 minutes, with alerts by email and by the mobile app. Its monthly report gives you the availability figure for the evaluation.
- **Twilio:** the spending triggers from part 9.
- **SMS counter:** each school's dashboard shows SMS sent this month against the limit (BR-25).
- **Disk space:** once a week, run `df -h /` on the server. Above 80 % used, delete old backups or move to a bigger server.

---

## 16. Installing a new version

When the code changes (bug fixes during the pilot):

1. On your PC, run the tests (`./mvnw verify`, `flutter test`, `npm test`), then commit and push to GitHub.
2. On the server:
   ```bash
   cd /opt/schoollink/deploy
   ./backup.sh                       # always back up before an update
   git pull
   docker compose up -d --build
   docker compose logs -f backend    # watch it start; Ctrl+C to stop watching
   ```
   Database changes (Flyway migrations) apply by themselves at start-up.
3. If the app changed, build and distribute it again (parts 12–13).

**Do updates outside school hours**, for example in the evening after 18:00. The API is down for about 30 seconds while the backend restarts. Teachers' phones keep their records offline and send them afterwards, but the web dashboard briefly shows errors.

**If an update goes wrong**, go back to the previous version:

```bash
git log --oneline -5              # find the previous version's commit
git checkout <commit>
docker compose up -d --build
```

If the bad version had already changed the database, also restore the backup taken before the update (part 14.3).

---

## 17. Everyday commands and troubleshooting

All commands run in `/opt/schoollink/deploy` on the server.

| To… | Run |
| --- | --- |
| See what is running | `docker compose ps` |
| Read the backend's log | `docker compose logs --tail 200 backend` |
| Follow the log live | `docker compose logs -f backend` (Ctrl+C to stop) |
| Restart everything | `docker compose restart` |
| Stop everything | `docker compose down` (the data stays) |
| Open the database | `docker compose exec db psql -U schoollink` (type `\q` to quit) |
| Change a school's SMS limit (BR-25) | `docker compose exec db psql -U schoollink -c "update school set sms_monthly_cap = 800 where name = 'School name'"` |
| See disk usage | `df -h /` |
| Free disk taken by old Docker images | `docker image prune -f` |

**Never run `docker compose down -v`.** The `-v` deletes the database volume, which means all the data.

**Common problems:**

| Symptom | Likely cause and fix |
| --- | --- |
| Browser says the site can't be reached | DNS doesn't point to the server yet (part 7), or the firewall blocks 80/443 (`sudo ufw status`). |
| Browser warns the certificate is not valid | Caddy could not get the certificate. Run `docker compose logs caddy`. Usually the DNS record is wrong, or port 80 is closed: Let's Encrypt needs it. |
| `backend` keeps restarting | Run `docker compose logs backend` and read the last lines. A message naming `JWT_SECRET`, `FIREBASE_CREDENTIALS` or `TWILIO` means that setting is missing or wrong in `.env`. "Cannot read the Firebase service-account file" means the key file is missing or not readable (part 10.2). |
| `docker compose` says `set DB_PASSWORD in .env` | That line in `.env` is empty. |
| Parents get no push | The app was built on a machine without `mobile\android\app\google-services.json`, or with the wrong `API_BASE_URL` (part 12). The parent may also have refused notifications in the phone's settings. |
| Parents get no SMS | Check, in this order: Twilio credit, Geo permissions for Cameroon, sender ID registration, the school's monthly limit, and Console → Monitor → Logs → Messaging for the error code. |
| Server slow or out of memory | `free -h` and `docker stats`. Two schools fit easily in 4 GB. If not, resize the server in Hetzner (CX33): a reboot, nothing to reinstall. |

---

## 18. Go-live checklist

**Before the first day:**
- [ ] Written authorisation from both head teachers; ethics approval if required (SPEC §11)
- [ ] Parent information notice and consent form, in French and English, handed out
- [ ] `https://YOUR_DOMAIN` loads with a padlock; `/actuator/health` shows `UP`
- [ ] Twilio upgraded, Cameroon allowed, sender ID approved, spending triggers set
- [ ] A test SMS reached a real MTN number and a real Orange number (invite yourself as a guardian)
- [ ] A test push reached a real phone with the production app
- [ ] Both schools: calendar, periods, classes, timetable, staff, students and letterhead in place
- [ ] Each school's SMS limit set to what you can afford
- [ ] Nightly backup running (`backups/backup.log` shows recent lines) and a restore tested
- [ ] UptimeRobot monitoring and alerting you
- [ ] `.env`, the Firebase key and the upload key backed up off the server
- [ ] App downloadable (link or QR code) or published on Google Play
- [ ] Staff trained: teachers (roll call, offline use), assistant DMs (lateness), DMs (alerts, justification, cancellation, report)
- [ ] The test records you made during the checks are cancelled

**During the pilot:**
- Daily: glance at the UptimeRobot alerts and each school's dashboard (sessions recorded, SMS counter).
- Weekly: copy the backups to your PC, check disk space and Twilio spending.
- Monthly: Ubuntu updates install by themselves; reboot the server when `ssh` shows "System restart required", in the evening.

**At the end of the pilot:**
- Take the research export (anonymised).
- Decide with the schools whether to continue. If not: final backup, then delete the server, the Twilio sender and the data after the retention period (SPEC §11).
