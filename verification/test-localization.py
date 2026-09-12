from pathlib import Path
import re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
def strings(folder):return {x.attrib['name']:x.text or '' for x in ET.parse(root/'app/src/main/res'/folder/'strings.xml').getroot()}
base=strings('values')
for folder in ['values-zh-rCN','values-de','values-fr']:
    translated=strings(folder)
    assert set(translated)==set(base),(folder,'missing/extra keys')
    for key,value in base.items():
        assert sorted(re.findall(r'%\d+\$[ds]',value))==sorted(re.findall(r'%\d+\$[ds]',translated[key])),(folder,key,'format placeholders')
        assert translated[key].strip(),(folder,key,'empty')
for p in (root/'app/src/main/java').rglob('*'):
    if p.suffix in ('.java','.kt'):assert not re.search('[\u3400-\u9fff]',p.read_text(encoding='utf-8')),(p,'hardcoded Chinese')
print('PASS:',len(base),'matching keys in four locales; placeholders and source extraction')
