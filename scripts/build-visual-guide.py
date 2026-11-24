#!/usr/bin/env python3
"""Build the illustrated HTML/offline guide and optional PDF from reviewed content.

No network, JavaScript, external fonts or model-generated screenshots are used.
HTML embeds the retained original PNGs; PDF uses those same files and vector art.
"""
import argparse
import base64
import hashlib
import html
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
START = "<!-- meshlit-visual-guide:start -->"
END = "<!-- meshlit-visual-guide:end -->"
ESC = html.escape

CSS = """
.vg{--ink:#182d37;--muted:#526773;--line:#dbe4e5;--accent:#b54420;color:var(--ink);font:17px/1.65 system-ui,-apple-system,sans-serif;max-width:1120px;margin:auto;padding:24px}
.vg *{box-sizing:border-box}.vg a{color:#963a1e;text-underline-offset:3px;overflow-wrap:anywhere}.vg a:focus-visible{outline:3px solid #b54420;outline-offset:3px}
.vg .vg-hero{background:#183b43;color:#fff;border-radius:24px;padding:48px 42px;margin:0 0 24px}.vg .vg-kicker{font-size:12px;letter-spacing:.16em;text-transform:uppercase;font-weight:750;color:#b9d5d4}.vg h1{font-size:clamp(34px,5vw,59px);line-height:1.08;letter-spacing:-.045em;margin:20px 0;max-width:850px;color:inherit}.vg .vg-deck{max-width:700px;font-size:20px;color:#e0eceb}.vg .vg-tag{display:inline-block;font-size:12px;border:1px solid #8eafaf;padding:5px 10px;margin:10px 8px 0 0;border-radius:30px}
.vg .vg-about,.vg .vg-toc{padding:22px 26px;background:#fff;border:1px solid var(--line);border-radius:18px;margin:20px 0}.vg .vg-toc ol{columns:2;column-gap:36px;padding-left:24px}.vg .vg-toc li{break-inside:avoid;padding:5px 0}.vg .vg-toc a{text-decoration:none}
.vg section{border:1px solid var(--line);border-radius:22px;background:white;padding:30px;margin:22px 0;scroll-margin-top:20px;box-shadow:none}.vg h2{font-size:29px;line-height:1.2;letter-spacing:-.025em;margin:8px 0 18px;color:var(--ink)}.vg h3{font-size:17px;margin:22px 0 8px}.vg p{margin:12px 0}.vg .vg-platform{font-size:12px;font-weight:750;text-transform:uppercase;letter-spacing:.09em;color:#637c83}.vg .vg-route{padding:10px 14px;border-left:3px solid #cb5b37;background:#fff5ef;font-size:14px;margin:15px 0}
.vg .vg-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(180px,290px);gap:30px;align-items:start}.vg .vg-layout.vg-wide{display:block}.vg figure{margin:10px 0 22px}.vg figure img{display:block;max-width:100%;width:auto;max-height:610px;height:auto;margin:auto;border:1px solid #dbe4e5;border-radius:15px}.vg .vg-wide figure img{max-height:440px}.vg figcaption{font-size:12px;line-height:1.55;color:var(--muted);margin-top:10px}.vg svg{width:100%;height:auto;margin:14px 0;display:block;border-radius:15px}.vg .vg-steps{list-style:none;padding:0;counter-reset:steps}.vg .vg-steps li{counter-increment:steps;position:relative;padding-left:43px;margin:17px 0}.vg .vg-steps li:before{content:counter(steps,decimal-leading-zero);position:absolute;left:0;top:1px;color:#a33e22;font-size:13px;font-weight:800;background:#fff1e8;border-radius:9px;padding:3px 6px}.vg .vg-steps strong{display:block;margin-bottom:2px}
.vg table{width:100%;border-collapse:collapse;font-size:14px;line-height:1.5;margin:20px 0;table-layout:fixed}.vg th,.vg td{text-align:left;padding:12px;vertical-align:top;border-bottom:1px solid var(--line);overflow-wrap:anywhere}.vg th{color:#fff;background:#244850}.vg td:first-child{width:31%;font-weight:650}.vg tr:nth-child(even) td{background:#f4f8f7}.vg .vg-success{border-left:3px solid #287c72;padding:12px 16px;background:#eef7f3;font-size:15px}.vg .vg-note{color:var(--muted);font-size:14px}.vg .vg-refs{font-size:12px;color:var(--muted);border-top:1px solid var(--line);padding-top:12px;margin-top:20px}.vg .vg-back{font-size:13px;display:inline-block;margin-top:8px}.vg-reference-title{font:700 26px system-ui;padding:32px 10px}
@media(max-width:680px){.vg{padding:12px;font-size:16px}.vg .vg-hero{padding:30px 24px}.vg section{padding:22px 18px}.vg .vg-toc ol{columns:1}.vg .vg-layout{display:flex;flex-direction:column}.vg .vg-layout figure{width:100%;max-width:340px;align-self:center}.vg h2{font-size:25px}.vg th,.vg td{padding:9px 7px;font-size:13px}.vg .vg-deck{font-size:18px}}
@media print{.vg{max-width:none;padding:0;font-size:10pt}.vg .vg-hero{background:#183b43!important;print-color-adjust:exact}.vg section{break-before:page;border:0;padding:0}.vg .vg-back{display:none}.vg .vg-layout{grid-template-columns:1fr 180px}.vg .vg-layout img{max-height:380px}.vg .vg-toc ol{columns:2}.vg a{color:inherit}.vg .vg-toc{break-after:page}}
"""


def artwork(name):
    """One primitive scene is rendered as both SVG and PDF vector graphics."""
    shapes = []
    def rect(x, y, w, h, fill): shapes.append(("rect", x, y, w, h, fill))
    def text(x, y, value, size=17, fill="#183b43"): shapes.append(("text", x, y, value, size, fill))
    def line(x, y, xx, yy): shapes.append(("line", x, y, xx, yy))
    rect(0, 0, 900, 250, "#f0f6f4")
    if name == "routes":
        for x, title, sub in [(25, "Your task", "Choose permissions"), (330, "Chosen runtime", "Local or approved host"), (635, "Observed result", "Reply, receipts, Stop")]:
            rect(x, 66, 240, 114, "#ffffff"); text(x+18, 105, title, 22); text(x+18, 139, sub, 15)
        line(265, 124, 325, 124); line(570, 124, 630, 124)
        text(25, 221, "Conceptual route. Availability and approval are checked separately.", 14)
    elif name == "cpu":
        text(25, 30, "Samsung: measured short-request wall time", 21)
        source=json.loads((DOCS/'desktop/evidence/multicore-20261102/summary.json').read_text())
        values=[(r['workers'],r['medianRequestMs']/1000) for r in source['rows']]
        for i,(workers,seconds) in enumerate(values):
            x=44+i*143;h=seconds/7.319*120
            rect(x, 188-h, 76, h, "#287c72" if workers==6 else "#c6d8d5")
            text(x, 206, str(workers)+" workers", 13); text(x, 178-h, f"{seconds:.3f}s", 15)
        text(25, 238, "Five warm samples each. Different policy builds; see limitations below. Not voice latency.", 14)
    elif name == "trust":
        labels=[("Network path", "Reachable IP / VPN"),("TLS identity", "Verified certificate"),("Owner approval", "Token and scope"),("Actual work", "Observed outcome")]
        for i,(title,sub) in enumerate(labels):
            x=20+i*225;rect(x,70,205,110,"#ffffff");text(x+12,109,title,19);text(x+12,140,sub,14)
            if i<3:line(x+205,125,x+220,125)
        text(25,224,"Passing one boundary does not grant the next. Keep Stop and revocation available.",14)
    elif name == "teams":
        for x,title,sub in [(25,"Lead","Assigned task"),(330,"Worker","Correlated reply"),(635,"Reviewer","Check the result")]:
            rect(x,66,240,114,"#ffffff");text(x+18,108,title,23);text(x+18,143,sub,15)
        line(265,110,325,110);line(325,143,265,143);line(570,125,630,125)
        text(25,221,"Conceptual team exchange. Selected members, call limits and grants remain enforced.",14)
    else: raise ValueError(name)
    return shapes


def svg_art(name):
    out=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 250" role="img" aria-label="'+ESC(name)+' diagram"><g font-family="Arial, sans-serif">']
    for s in artwork(name):
        if s[0]=="rect":
            _,x,y,w,h,c=s;out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{c}"/>')
        elif s[0]=="text":
            _,x,y,t,size,c=s;out.append(f'<text x="{x}" y="{y}" font-size="{size}" fill="{c}">{ESC(t)}</text>')
        else:
            _,x,y,xx,yy=s;out.append(f'<path d="M{x} {y} L{xx} {yy}" stroke="#b54420" stroke-width="3"/>')
    return ''.join(out)+"</g></svg>"


def asset(path):
    p=(DOCS/path).resolve()
    if not p.is_relative_to(ROOT) or not p.is_file(): raise ValueError(f"Invalid asset: {path}")
    return p


def chapter_html(c, number, offline=False):
    figure=''
    if f:=c.get('figure'):
        data=base64.b64encode(asset(f['path']).read_bytes()).decode()
        figure=f'<figure><img src="data:image/png;base64,{data}" alt="{ESC(f["alt"],quote=True)}" loading="eager"><figcaption>{ESC(f["caption"])}</figcaption></figure>'
    steps='<ol class="vg-steps">'+''.join(f'<li><strong>{ESC(t)}</strong>{ESC(v)}</li>' for t,v in c.get('steps',[]))+'</ol>'
    table=''
    if t:=c.get('table'):
        table='<table><thead><tr>'+''.join('<th scope="col">'+ESC(x)+'</th>' for x in t['headers'])+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+ESC(x)+'</td>' for x in row)+'</tr>' for row in t['rows'])+'</tbody></table>'
    diag=svg_art(c['diagram']) if c.get('diagram') else ''
    links=' · '.join(ESC(label)+': '+ESC(url) if offline else f'<a href="{ESC(url,quote=True)}">{ESC(label)}</a>' for label,url in c.get('links',[]))
    refs=' · '.join(ESC(p.split('#')[0]) if offline else f'<a href="{ESC(p,quote=True)}">{ESC(p.split("#")[0])}</a>' for p in c.get('refs',[]))
    wide=' vg-wide' if not figure or c['id']=='studio' else ''
    return f'''<section id="vg-{c['id']}"><div class="vg-platform">{number:02d} / {ESC(c['platform'] if 'platform' in c else 'Android + Studio')}</div><h2>{ESC(c['title'])}</h2>{('<div class="vg-route">'+ESC(c['route'])+'</div>') if c.get('route') else ''}<p>{ESC(c['intro'])}</p>{diag}{table}<div class="vg-layout{wide}"><div>{steps}</div>{figure}</div><div class="vg-success"><strong>What success looks like</strong><br>{ESC(c['success'])}</div>{''.join('<p class="vg-note">'+ESC(n)+'</p>' for n in c.get('notes',[]))}<div class="vg-refs">Reference: {refs}{('<br>'+links) if links else ''}</div><a class="vg-back" href="#vg-contents">Back to contents</a></section>'''


def fragment(data, offline=False):
    toc=''.join(f'<li><a href="#vg-{c["id"]}">{ESC(c["title"])}</a></li>' for c in data['chapters'])
    offline_note='<p>Reference filenames and official URLs below are plain text in this offline edition. Use the table of contents or Find to read here; the web/PDF edition includes clickable external references.</p>' if offline else ''
    return f'''<style>{CSS}</style><article class="vg" id="visual-guide"><header class="vg-hero"><div class="vg-kicker">MESHLIT / ILLUSTRATED FIELD GUIDE</div><h1>Make it work.<br>Know what is running.</h1><p class="vg-deck">{ESC(data['subtitle'])}</p><span class="vg-tag">{data['date']}</span><span class="vg-tag">20 practical chapters</span><span class="vg-tag">Offline screenshots + vector graphics</span></header><div class="vg-about"><strong>Use this guide</strong><p>Follow numbered steps, compare the expected result, then use the reference for advanced details. Images are original retained captures with source labels; diagrams explain the system and are not screenshots. Use your browser's Find or the app's Find in offline guide field.</p><p>The Android screenshots in this walkthrough are physical Samsung captures from November 2-3. Desktop pictures are retained October 26 component renders and do not establish native-window acceptance. Private account details, pairing QR codes and credentials are excluded.</p>{offline_note}</div><nav class="vg-toc" id="vg-contents" aria-label="Illustrated guide contents"><strong>Choose a task</strong><ol>{toc}</ol></nav>{''.join(chapter_html(c,i,offline) for i,c in enumerate(data['chapters'],1))}<footer class="vg-note">Meshlit illustrated guide / {data['date']} / No scripts, external fonts, tracking or network images. See the current progress and evidence records for exact build qualification.</footer></article>'''


def sync_existing(path, body):
    text=path.read_text()
    block=START+body+'<h2 class="vg-reference-title">Extended feature reference</h2>'+END
    if START in text:
        text=re.sub(re.escape(START)+'.*?'+re.escape(END),lambda _:block,text,flags=re.S)
    else:
        text=re.sub(r'<header\b[^>]*>.*?</header>', '', text, count=1, flags=re.S)
        if '<main>' not in text: raise ValueError(f'Missing guide main: {path}')
        text=text.replace('<main>','<main>'+block,1)
    if path == ROOT/'app/src/main/assets/help/index.html':
        for name in ['chat.png','models.png','settings.png']:
            image_path=ROOT/'app/src/main/assets/help/images'/name
            if not image_path.is_file():image_path=DOCS/'assets/screenshots'/name
            encoded=base64.b64encode(image_path.read_bytes()).decode()
            text=text.replace('src="images/'+name+'"','src="data:image/png;base64,'+encoded+'"')
    path.write_text(text)


def build_pdf(data, output):
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_LEFT
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.utils import ImageReader
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, Image, KeepTogether
    from reportlab.graphics.shapes import Drawing, Rect, String, Line
    from reportlab.lib.pagesizes import A4
    width,height=A4; usable=width-84
    styles=getSampleStyleSheet()
    styles.add(ParagraphStyle('GuideBody',fontName='Helvetica',fontSize=9.6,leading=13.1,textColor=colors.HexColor('#263e48'),spaceAfter=8))
    styles.add(ParagraphStyle('GuideTitle',fontName='Helvetica-Bold',fontSize=25,leading=29,textColor=colors.HexColor('#183b43'),spaceAfter=12))
    styles.add(ParagraphStyle('GuideCaption',fontName='Helvetica',fontSize=7.6,leading=10.5,textColor=colors.HexColor('#526773'),spaceAfter=8))
    styles.add(ParagraphStyle('GuideKicker',fontName='Helvetica-Bold',fontSize=8,leading=11,textColor=colors.HexColor('#a64325'),spaceAfter=9))
    styles.add(ParagraphStyle('GuideCell',parent=styles['GuideBody'],fontSize=8.5,leading=11,spaceAfter=0))
    def p(s,style='GuideBody'):return Paragraph(ESC(s),styles[style])
    def step_flow(c):
        return [Paragraph(f'<font color="#a64325"><b>{i:02d}</b></font> <b>{ESC(t)}</b><br/>{ESC(v)}',styles['GuideBody']) for i,(t,v) in enumerate(c.get('steps',[]),1)]
    def drawing(name):
        scale=usable/900; d=Drawing(usable,250*scale)
        for s in artwork(name):
            if s[0]=='rect':
                _,x,y,w,h,c=s;d.add(Rect(x*scale,(250-y-h)*scale,w*scale,h*scale,rx=6,ry=6,fillColor=colors.HexColor(c),strokeColor=None))
            elif s[0]=='text':
                _,x,y,t,size,c=s;d.add(String(x*scale,(250-y)*scale,t,fontName='Helvetica',fontSize=size*scale,fillColor=colors.HexColor(c)))
            else:
                _,x,y,xx,yy=s;d.add(Line(x*scale,(250-y)*scale,xx*scale,(250-yy)*scale,strokeColor=colors.HexColor('#b54420'),strokeWidth=1.5))
        return d
    def furniture(canvas,doc):
        canvas.setStrokeColor(colors.HexColor('#dbe4e5'));canvas.line(42,39,width-42,39)
        canvas.setFont('Helvetica',8);canvas.setFillColor(colors.HexColor('#526773'))
        canvas.drawString(42,26,'MESHLIT / ILLUSTRATED USER GUIDE / '+data['date'])
        canvas.drawRightString(width-42,26,f'{doc.page:02d}')
        if doc.page>2:
            canvas.setFont('Helvetica-Bold',8);canvas.drawString(42,height-31,'ANDROID + STUDIO / PRACTICAL WORKFLOWS')
    story=[Spacer(1,72),p('MESHLIT / FIELD GUIDE','GuideKicker'),Paragraph('Make it work.<br/>Know what<br/>is running.',ParagraphStyle('Cover',parent=styles['GuideTitle'],fontSize=46,leading=49,spaceAfter=24)),p(data['subtitle']),Spacer(1,20),drawing('routes'),Spacer(1,25),p('20 practical chapters. Original screenshots. Explanatory vector diagrams.','GuideKicker'),p('Follow a task, check the result, and keep permissions specific. This guide covers Android and the available Studio surfaces, with historical screenshots and unqualified features labeled.'),p('Edition '+data['date']+'. Current source and development candidates; platform and build availability vary.'),PageBreak(),p('Choose a task','GuideTitle')]
    for i,c in enumerate(data['chapters'],1): story.append(p(f'{i:02d}   {c["title"]}'))
    story += [Spacer(1,10),p('About the images','GuideKicker'),p('Android images: physical Samsung captures from November 2-3. Desktop images: retained October 26 Compose component renders, not native-window qualification. Credentials, account screens and private pairing QR codes are excluded.','GuideCaption')]
    for i,c in enumerate(data['chapters'],1):
        story += [PageBreak(),p(f'{i:02d} / {c.get("platform","Android + Studio").upper()}','GuideKicker'),p(c['title'],'GuideTitle')]
        if c.get('route'):story.append(p(c['route'],'GuideCaption'))
        story.append(p(c['intro']))
        if c.get('diagram'):story.extend([drawing(c['diagram']),Spacer(1,10)])
        if t:=c.get('table'):
            rows=[[p(x,'GuideCell') for x in t['headers']]]+[[p(x,'GuideCell') for x in r] for r in t['rows']]
            table=Table(rows,colWidths=[usable*.29,usable*.71],hAlign='LEFT')
            table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e2eeeb')),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7),('LINEBELOW',(0,0),(-1,-1),.4,colors.HexColor('#dbe4e5'))]))
            story.extend([table,Spacer(1,12)])
        if f:=c.get('figure'):
            img_path=asset(f['path']);iw,ih=ImageReader(str(img_path)).getSize()
            portrait=ih>iw
            if portrait:
                maxw=153;maxh=338;ratio=min(maxw/iw,maxh/ih)
                picture=Image(str(img_path),width=iw*ratio,height=ih*ratio)
                figure=[picture,Spacer(1,8),p(f['caption'],'GuideCaption')]
                layout=Table([[step_flow(c),figure]],colWidths=[usable-173,173],hAlign='LEFT')
                layout.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(0,0),0),('RIGHTPADDING',(0,0),(0,0),17),('LEFTPADDING',(1,0),(1,0),10),('RIGHTPADDING',(1,0),(1,0),0),('TOPPADDING',(0,0),(-1,-1),0),('BOTTOMPADDING',(0,0),(-1,-1),0)]))
                story.append(layout)
            else:
                ratio=min(usable/iw,208/ih);story.extend([Image(str(img_path),width=iw*ratio,height=ih*ratio),p(f['caption'],'GuideCaption'),*step_flow(c)])
        else:story.extend(step_flow(c))
        story.append(KeepTogether([Spacer(1,6),p('WHAT SUCCESS LOOKS LIKE','GuideKicker'),p(c['success'])]))
        for note in c.get('notes',[]):story.append(p(note,'GuideCaption'))
        refs='Reference: '+', '.join(c.get('refs',[]))
        story.append(p(refs,'GuideCaption'))
        for label,url in c.get('links',[]):story.append(Paragraph(f'{ESC(label)}: <link href="{ESC(url,quote=True)}" color="#963a1e">{ESC(url)}</link>',styles['GuideCaption']))
    output.parent.mkdir(parents=True,exist_ok=True)
    doc=SimpleDocTemplate(str(output),pagesize=A4,leftMargin=42,rightMargin=42,topMargin=52,bottomMargin=53,title=data['title'],author='Meshlit',pageCompression=1)
    doc.build(story,onFirstPage=furniture,onLaterPages=furniture)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--pdf',type=Path);parser.add_argument('--sync-offline',action='store_true');args=parser.parse_args()
    data=json.loads((DOCS/'visual-guide-content.json').read_text())
    ids=[c['id'] for c in data['chapters']]
    assert len(ids)==len(set(ids))==20
    for c in data['chapters']:
        for ref in c.get('refs',[]):
            assert (DOCS/ref.split('#')[0]).is_file(), f'Missing reference: {ref}'
    body=fragment(data)
    document='<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+ESC(data['title'])+'</title><style>body{margin:0;background:#f2f6f5}</style></head><body>'+body+'</body></html>'
    (DOCS/'visual-guide.html').write_text(document)
    if args.sync_offline:
        sync_existing(DOCS/'user-guide.html',body)
        sync_existing(ROOT/'app/src/main/assets/help/index.html',fragment(data,offline=True))
    if args.pdf:build_pdf(data,args.pdf.resolve())
    manifest={'date':data['date'],'chapters':len(ids),'screenshots':[],'diagrams':sorted({c['diagram'] for c in data['chapters'] if c.get('diagram')}),'networkAssets':False,'scripts':False}
    for c in data['chapters']:
        if f:=c.get('figure'):
            path=asset(f['path']);manifest['screenshots'].append({'path':f['path'],'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'caption':f['caption']})
    (DOCS/'desktop/evidence/visual-guide-20261102.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(f'Built {len(ids)} chapters, {len(manifest["screenshots"])} original screenshots, {len(manifest["diagrams"])} vector graphics; offline sync={args.sync_offline}')


if __name__=='__main__':main()
