# BhuRaksha Backend (SIH26001)

The Spring API now calls the trained FastAPI ML service while keeping the
frontend response contract unchanged.

## Run the complete application

First train and start the ML service using the commands in
[`../../ml-service/README.md`](../../ml-service/README.md). Then start this backend:

```bash
mvn spring-boot:run
```

The backend requires JDK 17 or newer. If Maven uses an older Java version,
point `JAVA_HOME` to a compatible JDK before starting it. On this computer,
for example:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
```

Test it with:

```
GET http://localhost:8081/api/risk?place=Gangtok
```

To explicitly request a Moderate/High email alert through the API, use:

```
GET http://localhost:8081/api/risk?place=Gangtok&sendAlert=true
```

## Immediate email alerts

Each explicit frontend **Check risk** action sends an email to
`arungowdru29@gmail.com` when the result is `MODERATE` or `HIGH`. Map loading
and ordinary API reads do not send emails. The backend waits for SMTP to accept
the message before it returns the risk response, so the alert is triggered
immediately by that check.

Before starting the backend, create a [Google App Password](https://myaccount.google.com/apppasswords)
for the Gmail account that will send alerts, then set it only in your terminal:

```powershell
$env:MAIL_USERNAME = "your-sending-gmail@gmail.com"
$env:MAIL_PASSWORD = "your-16-character-google-app-password"
mvn spring-boot:run
```

Do not use or commit a normal Gmail password. Optional environment variables
are `ALERT_EMAIL_ENABLED`, `ALERT_EMAIL_RECIPIENT`, `ALERT_EMAIL_SENDER`, and
`ALERT_EMAIL_COOLDOWN_SECONDS` (default `0`, which sends on every user check).

The backend maps each supported place to one of the districts represented in
the supplied training data. The response's risk level, score, rainfall, and
most-risky slope range are calculated by the model service. If it is not
running, the API returns HTTP 503 rather than returning fabricated values.

The summary endpoint uses the latest historical district/month features as a
baseline. Connect a live rainfall and field-observation source to `POST
/predict` in the Python service before treating this as an operational warning
system.
