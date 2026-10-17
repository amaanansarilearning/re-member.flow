import json
import re

transcript = r'C:\Users\HP\.gemini\antigravity\brain\6dfa366e-0571-4358-9ed8-655d88518e9f\.system_generated\logs\transcript_full.jsonl'
files_to_recover = {
    'styles.scss': {'path': r'C:\Users\HP\Desktop\Gym Management Appliation\frontend\src\styles.scss', 'found': False},
    'app.scss': {'path': r'C:\Users\HP\Desktop\Gym Management Appliation\frontend\src\app\app.scss', 'found': False},
    'index.html': {'path': r'C:\Users\HP\Desktop\Gym Management Appliation\frontend\src\index.html', 'found': False}
}

with open(transcript, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get('type') == 'GENERIC':
                content = data.get('content', '')
                for key, info in files_to_recover.items():
                    if not info['found'] and f'/{key}' in content and 'File Path:' in content:
                        print(f'Found {key} in transcript.')
                        lines = content.split('\n')
                        extracting = False
                        extracted = []
                        for l in lines:
                            if 'The following code has been modified' in l:
                                extracting = True
                                continue
                            if 'The above content shows' in l or 'The above content does NOT show' in l:
                                extracting = False
                                continue
                            if extracting:
                                match = re.match(r'^\d+:\s(.*)$', l)
                                if match:
                                    extracted.append(match.group(1))
                                elif re.match(r'^\d+:$', l):
                                    extracted.append('')
                        if extracted:
                            with open(info['path'], 'w', encoding='utf-8') as out_f:
                                out_f.write('\n'.join(extracted))
                            info['found'] = True
                            print(f'Restored {key}')
        except Exception as e:
            pass
