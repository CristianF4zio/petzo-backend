# Stagehand import worker

Polls `GET /internal/imports/next` every 10 s, extracts up to 10 adoption posts from a public Instagram profile with Claude Haiku 5.5 and sends them to `POST /internal/imports/{id}/pets` as drafts (`source=instagram`). Token usage goes back in the status call and lands in `ai_logs`.

```
cp .env.example .env   # PETZO_API_URL, INTERNAL_SECRET, ANTHROPIC_API_KEY
npm install
npx playwright install chromium   # only if no local Chrome is found
npm start
```

Runs on your PC (no open ports): it only makes outbound calls to the API. Launch imports from the admin panel.
