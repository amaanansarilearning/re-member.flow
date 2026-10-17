# AWS Free Tier Deployment Guide — GymFlow Application

This guide takes you through deploying the **GymFlow (Spring Boot + Angular + MySQL)** application onto an **AWS Free Tier EC2 instance** from start to finish.

> [!NOTE]
> **No Local Docker Needed**:
> You do **NOT** need Docker installed on your Windows machine. Docker and Docker Compose will be installed directly on the free Ubuntu EC2 server in AWS. Your local computer only needs Git or PowerShell to upload your code.

---

## 1. What Changes Were Made to Properties & Configs

Before deployment, the following changes were already updated in your project:

1. **`docker-compose.yml`**:
   - `WEB_BIND_ADDRESS` defaults to `0.0.0.0` (binds to all public network interfaces instead of localhost).
   - `WEB_PORT` defaults to `80` (standard HTTP port so visitors don't have to specify `:4200` in the URL).
   - Added `GYM_APP_URL` to the `api` service environment so password reset emails point to your EC2 IP.

2. **`frontend/nginx.conf`**:
   - Already configured as a reverse proxy: requests to `/api/` go to the Spring Boot backend (`http://api:8080`), and all other requests serve the Angular single-page frontend. This eliminates any CORS issues across browser origins.

3. **`.env` Production Values**:
   - On the EC2 server, you will configure `.env` with your EC2 Public IP address, strong database passwords, and cryptographic keys.

---

## 2. AWS Free Tier Limits & Best Practices

To guarantee you **never incur charges**:

- **EC2 Compute**: 750 hours/month of `t2.micro` (or `t3.micro` where t2 is unavailable) for 12 months. Since 1 month has 744 hours max, **one instance running 24/7 is 100% free**.
- **EBS Storage**: Up to 30 GB of gp2/gp3 SSD storage is free. We will configure 25 GB.
- **Instance Count**: Keep only **1** EC2 instance running.
- **AWS Budget Alert**: Set up a \$0.01 threshold budget alert (Step 10) so AWS alerts you immediately if any billable usage occurs.

---

## 3. Step 1: Launch an AWS Free Tier EC2 Instance

1. Log in to the [AWS Management Console](https://console.aws.amazon.com/).
2. Select your nearest region in the top-right header (e.g. `ap-south-1` Mumbai, `us-east-1` N. Virginia, `eu-central-1` Frankfurt).
3. In the search bar at the top, type **EC2** and click on the EC2 service.
4. Click the orange **Launch Instance** button.
5. Fill in the launch form:

| Setting               | Value                                                  | Why / Notes                                                                                                                                                     |
| :-------------------- | :----------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Name**              | `gym-server`                                           | Any descriptive name                                                                                                                                            |
| **OS Image (AMI)**    | **Ubuntu Server 24.04 LTS** (HVM), SSD Volume Type     | Free Tier eligible, 64-bit (x86)                                                                                                                                |
| **Instance Type**     | **t3.micro** _(or **t2.micro**)_                       | Marked **Free tier eligible** (2 vCPUs, 1 GB RAM)                                                                                                               |
| **Key pair (login)**  | Click **Create new key pair**                          | Name: `gym-key`<br>Type: `RSA`<br>Format: `.pem`<br>Save the downloaded `.pem` file to `Downloads` on your PC. _(Only needed if connecting via PowerShell/SSH)_ |
| **Network Settings**  | Select **Create security group**                       | Check the following:                                                                                                                                            |
|                       | ✔ **Allow SSH traffic from**                           | `Anywhere` (or `My IP`)                                                                                                                                         |
|                       | ✔ **Allow HTTP traffic from the internet** (Port 80)   | Required for public web access                                                                                                                                  |
|                       | ✔ **Allow HTTPS traffic from the internet** (Port 443) | For future SSL/TLS                                                                                                                                              |
| **Configure Storage** | **25 GiB** `gp3`                                       | Free Tier includes up to 30 GiB                                                                                                                                 |

> [!NOTE]
> **About `t3.micro` Memory:**
> `t3.micro` has **2 vCPUs and 1 GiB (1024 MB) RAM** (whereas `t3.small` has 2 GiB RAM, but is outside the Free Tier). Having 2 vCPUs on `t3.micro` makes compiling much faster than `t2.micro`! However, because building Angular + Spring Boot + MySQL simultaneously can briefly spike memory usage past 1.5 GB, the Swap File in Step 3 is our zero-cost safety net.

> [!CAUTION]
> Do **NOT** open port 3306 (MySQL) or port 8080 (Spring API) in the security group. Only port 80 and port 22 need to be open to the internet. Docker isolates and connects MySQL and Spring Boot internally.

6. Click **Launch instance**.
7. Wait 1–2 minutes, click on **Instances** in the left sidebar, and copy your instance's **Public IPv4 address** (e.g. `13.233.54.120`).

---

## 4. Step 2: Connect to Your EC2 Server

### 🤔 Why do we need this step?

Your EC2 instance is a brand-new, empty virtual Linux computer running in AWS data centers with no screen, keyboard, or mouse.
Because **you don't have Docker on your local Windows PC**, the actual Docker installation, git cloning, building, and running of your app **must happen directly inside this remote Linux computer**.

Connecting allows you to type terminal commands directly into the EC2 machine. You have two easy ways to connect:

---

### Method A (Recommended — 1-Click via Browser, No PowerShell or Keys needed!)

AWS provides a built-in web terminal so you don't even need to open PowerShell or manage `.pem` keys:

1. In the **AWS EC2 Console**, go to **Instances**.
2. Select your instance by checking the box next to `gym-server`.
3. Click the orange **Connect** button at the top of the screen.
4. Keep the default tab: **EC2 Instance Connect**.
5. Leave the username as `ubuntu`.
6. Click the orange **Connect** button at the bottom right.
7. A new browser tab opens with a black terminal screen ready for commands! You can now proceed directly to **Step 3**.

---

### Method B (From Windows PowerShell using SSH)

If you prefer running commands from your local Windows PowerShell terminal:

1. Open **PowerShell** on your Windows PC and navigate to where your key pair was saved:

   ```powershell
   cd C:\Users\HP\Downloads
   ```

2. Restrict `.pem` file permissions (Windows OpenSSH requires this):

   ```powershell
   icacls .\gym-key.pem /inheritance:r
   icacls .\gym-key.pem /grant:r "$($env:USERNAME):(R)"
   ```

3. Connect to the Ubuntu EC2 instance (replace with your instance's Public IP):
   ```powershell
   ssh -i .\gym-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
   ```
   _(When prompted with `Are you sure you want to continue connecting (yes/no/[fingerprint])?`, type `yes` and hit Enter)._

---

## 5. Step 3: Server Setup & Adding Swap Memory

Once inside the Ubuntu terminal (`ubuntu@ip-...:~$`), run these commands:

### 1. Enable 3 GB Swap File (Safety Net for Build & DB)

Even with 2 vCPUs on `t3.micro`, compiling Angular and Spring Boot inside Docker while running MySQL 8.4 can consume 1.5–2.5 GB of memory temporarily. A 3 GB swap file allocates space on your 25 GB SSD as virtual memory so builds never crash or run out of memory.

Run this block in the EC2 terminal:

```bash
sudo fallocate -l 3G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Verify swap is enabled:

```bash
free -h
```

_(You will see `Swap: 3.0Gi`)_.

---

### 2. Install Docker and Docker Compose on EC2

Run this block to install the latest Docker engine:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ca-certificates curl gnupg

# Add Docker GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker and plugins
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Grant permission to the ubuntu user
sudo usermod -aG docker ubuntu
```

Log out and reconnect so permissions take effect:

```bash
exit
```

Reconnect from PowerShell:

```powershell
ssh -i .\gym-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
```

Verify Docker is working without sudo:

```bash
docker --version
docker compose version
```

---

## 6. Step 4: Transfer Code to the EC2 Server

You have two simple options. Choose whichever you prefer:

### Option A: Using Git (Easiest)

If your project is pushed to GitHub/GitLab:

```bash
git clone https://github.com/<your-username>/<your-repo-name>.git gym-app
cd gym-app
```

### Option B: Upload Directly from Windows PowerShell (No Git required)

If you haven't pushed to GitHub, open a **new PowerShell window** on your Windows PC and upload the project files using `scp`:

```powershell
cd "c:\Users\HP\Desktop\Gym Management Appliation"

# Upload project files (skipping local build and node_modules folders)
scp -i C:\Users\HP\Downloads\gym-key.pem -r `
  docker-compose.yml .env.example backend frontend `
  ubuntu@<YOUR_EC2_PUBLIC_IP>:~/gym-app/
```

Then on your EC2 terminal:

```bash
cd ~/gym-app
```

---

## 7. Step 5: Configure the Production `.env` File on EC2

Generate a secure 32-byte JWT secret right on your EC2 terminal:

```bash
openssl rand -base64 32
```

Copy the output string.

Now create and open the `.env` file on EC2:

```bash
nano .env
```

Paste the following content, replacing `<YOUR_EC2_PUBLIC_IP>` with your instance's Public IPv4 address:

```env
# ==========================================
# DATABASE SETTINGS
# ==========================================
MYSQL_DATABASE=gymdb
MYSQL_USER=gymflow
MYSQL_PASSWORD=GymFlowSecretPass2026!
MYSQL_ROOT_PASSWORD=GymFlowRootSecretPass2026!
MYSQL_PORT=3306

# ==========================================
# JWT & APP SECURITY
# ==========================================
# Paste your generated 32-byte Base64 key here:
GYM_JWT_SECRET=VKod+0eEuMJbI3OunRac4UN4MLW1GoTbcruKpbgy/7Q=
GYM_JWT_EXPIRATION_MS=43200000

# ==========================================
# BUSINESS & TAX RULES
# ==========================================
GYM_BUSINESS_ZONE=Asia/Kolkata
GYM_RENEWAL_REMINDERS_CRON=0 */15 * * * *
GYM_TAX_CGST_RATE=9.00
GYM_TAX_SGST_RATE=9.00
GYM_TAX_IGST_RATE=18.00
GYM_TAX_INTERSTATE=false

# ==========================================
# PUBLIC NETWORK BINDING (Replace with EC2 Public IP)
# ==========================================
GYM_CORS_ORIGIN=http://<YOUR_EC2_PUBLIC_IP>
GYM_APP_URL=http://<YOUR_EC2_PUBLIC_IP>
WEB_BIND_ADDRESS=0.0.0.0
WEB_PORT=80

# ==========================================
# SMTP / EMAIL (Optional: leave defaults or provide Gmail app password)
# ==========================================
MAIL_HOST=localhost
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=noreply@gym.local
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
```

Save and exit `nano`:

- Press `Ctrl + O`, then hit `Enter`.
- Press `Ctrl + X`.

---

## 8. Step 6: Build & Start the Application

In the `~/gym-app` directory on EC2, run:

```bash
docker compose up --build -d
```

> [!NOTE]
> The initial build downloads Maven and Node.js dependencies inside the container and compiles both the Angular frontend and Spring Boot backend. Because of the swap memory we configured, this will build cleanly on the 1 GB instance in about 3–5 minutes.

Check the running containers:

```bash
docker compose ps
```

You should see 3 running containers:

- `gym-app-mysql-1` (Up, healthy)
- `gym-app-api-1` (Up)
- `gym-app-web-1` (Up, `0.0.0.0:80->80/tcp`)

To view live backend startup logs:

```bash
docker compose logs -f api
```

Look for:
`Started GymManagementApplication in ... seconds`
Press `Ctrl + C` to exit log viewing (the application continues running in the background).

---

## 9. Step 7: Access the App & Register Your Gym

1. Open your web browser on your computer.
2. Go to:
   ```
   http://13.49.145.53
   ```
3. You will see the **GymFlow** application interface.
4. Click **Set up a club** (or "Register New Gym" on the sign-in modal).
5. Enter your Gym / Organization name, a unique gym code (e.g. `IRONPEAK`), branch name, your full name, owner email, and password.
6. Submit the form. Your gym workspace and Owner account are automatically provisioned and activated!
7. Sign in with your owner credentials and explore your dashboard, branch management, membership packages, and team controls.

---

## 10. Step 8: Set Up Zero-Spend AWS Budget Alert (Cost Protection)

To make 100% sure you never get billed unexpectedly:

1. In the AWS Management Console, search for **AWS Budgets**.
2. Click **Create a budget**.
3. Choose **Templates (simplified)** ➔ Select **Zero spend budget**.
4. Give it a name: `FreeTierProtection`.
5. Enter your email address under **Email recipients**.
6. Click **Create budget**.
   AWS will email you immediately if your usage exceeds \$0.01.

---

## 11. Handy Management Commands on EC2

Run these commands inside `~/gym-app` on EC2:

| Action                         | Command                        |
| :----------------------------- | :----------------------------- |
| **Check container status**     | `docker compose ps`            |
| **View backend logs**          | `docker compose logs -f api`   |
| **View web / Nginx logs**      | `docker compose logs -f web`   |
| **View database logs**         | `docker compose logs -f mysql` |
| **Restart the app**            | `docker compose restart`       |
| **Stop the app**               | `docker compose down`          |
| **Rebuild after code changes** | `docker compose up --build -d` |
| **Check memory usage**         | `free -h` or `docker stats`    |

_(Note: Never run `docker compose down -v` unless you want to delete your database volume)._
