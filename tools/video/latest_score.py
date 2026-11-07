"""Prints the latest finished Premier League result as "Arsenal 2 - 1 Chelsea|FULL TIME", from ESPN's public scoreboard."""
import datetime
import json
import urllib.request

today = datetime.date.today()
for back in range(0, 8):
    day = (today - datetime.timedelta(days=back)).strftime("%Y%m%d")
    url = f"https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard?dates={day}"
    games = json.load(urllib.request.urlopen(url, timeout=15)).get("events", [])
    for g in reversed(games):
        if g["status"]["type"]["completed"]:
            home, away = sorted(g["competitions"][0]["competitors"], key=lambda c: c["homeAway"] != "home")
            print(f'{home["team"]["shortDisplayName"]} {home["score"]} - {away["score"]} {away["team"]["shortDisplayName"]}|FULL TIME')
            raise SystemExit
print("Glow score banners|Your teams, over anything you're watching")
