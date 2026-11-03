#!/usr/bin/env python3
"""Build leak-safe team possession profiles from nflverse weekly play-by-play parquet."""
import datetime, json, pathlib, sys
import pandas as pd
from urllib.request import urlretrieve

ROOT=pathlib.Path(__file__).resolve().parent
OUT=pathlib.Path('app/src/main/assets/nflverse_drive_profiles.json')
OUT.parent.mkdir(parents=True,exist_ok=True)
TODAY=datetime.datetime.now(datetime.timezone.utc).date()
frames=[]
for season in (2025,2026):
    url=f'https://github.com/nflverse/nflverse-data/releases/download/pbp/play_by_play_{season}.parquet'
    dest=ROOT/f'pbp_{season}.parquet'
    try:
        urlretrieve(url,dest)
        df=pd.read_parquet(dest)
        df['season_source']=season
        frames.append(df)
        print('Loaded nflverse',season,len(df))
    except Exception as exc:
        if season==2026: raise RuntimeError('2026 nflverse unavailable; refusing fake calibration') from exc
        print('Prior season unavailable:',exc)
    finally:
        dest.unlink(missing_ok=True)
if not frames:raise RuntimeError('No nflverse data')
df=pd.concat(frames,ignore_index=True)
required=['game_id','posteam','drive','season','week','game_date','play_type','drive_play_count','drive_time_of_possession']
missing=[x for x in required if x not in df.columns]
if missing:raise RuntimeError('Missing nflverse columns: '+str(missing))
df['date']=pd.to_datetime(df.game_date,errors='coerce').dt.date
df=df[(df.date<TODAY)&df.posteam.notna()&df.drive.notna()]
df=df[df.play_type.isin(['pass','run','punt','field_goal','qb_kneel','qb_spike'])]
def secs(x):
    if pd.isna(x):return None
    parts=str(x).split(':')
    try:return int(parts[-2])*60+int(parts[-1]) if len(parts)>=2 else None
    except ValueError:return None
# One drive per game/drive/posteam, no double counting of repeated drive fields.
drive=df.sort_values(['game_id','drive']).groupby(['season','week','game_id','posteam','drive'],as_index=False).agg(
    date=('date','first'), plays=('drive_play_count','max'), time=('drive_time_of_possession','first'),
    scrimmage=('play_type',lambda x:int(x.isin(['pass','run']).sum())),
    passes=('play_type',lambda x:int((x=='pass').sum())),
    rushes=('play_type',lambda x:int((x=='run').sum()))
)
drive['seconds']=drive.time.map(secs)
drive=drive[(drive.plays.between(1,25))&(drive.seconds.between(5,1000))]
if drive.empty:raise RuntimeError('No valid drives')
profiles=[]
for season in (2025,2026):
    for week in range(1,23):
        # Prior games ONLY: previous weeks in same season; 2025 baseline for 2026 early weeks.
        subset=drive[(drive.season==season)&(drive.week<week)]
        if season==2026:
            baseline=drive[(drive.season==2025)&(drive.week<=18)]
            subset=pd.concat([baseline,subset],ignore_index=True)
        for team,g in subset.groupby('posteam'):
            games=g.game_id.nunique()
            if games<2:continue
            scrim=int(g.scrimmage.sum());total=int(g.passes.sum()+g.rushes.sum())
            profiles.append(dict(season=season,week=week,team=team,games=games,drives=int(len(g)),
                drives_per_game=round(len(g)/games,3),plays_per_drive=round(float(g.plays.mean()),3),
                seconds_per_drive=round(float(g.seconds.mean()),3),
                seconds_per_play=round(float(g.seconds.sum())/max(1,scrim),3),
                pass_rate=round(float(g.passes.sum())/max(1,total),4)))
if not any(x['season']==2026 for x in profiles):raise RuntimeError('No 2026 profiles')
payload={'schema':'nflverse-v43-drive-profiles','source':'nflverse/nflverse-data pbp parquet',
         'generated_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
         'cutoff_date_exclusive':str(TODAY),'profiles':profiles}
OUT.write_text(json.dumps(payload,separators=(',',':')))
print('Wrote',OUT,'profiles',len(profiles),'bytes',OUT.stat().st_size)
