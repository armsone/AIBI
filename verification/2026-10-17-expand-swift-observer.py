import re
from pathlib import Path
s=Path('/Users/armsone/git/Stargram/iManagerAI/Features/Composer/ExternalAIBrowserView.swift').read_text()
a=s.index('static func observerScript(')
a=s.index('return """',a)+len('return """')
b=s.index('\n        """',a)
v=s[a:b]
v=re.sub(r'\\\(jsArray\(config\.\w+\)\)', '[]',v)
v=re.sub(r'\\\(provider[^\n]*? : "false"\)', 'false',v)
v=v.replace('\\(jsArray(provider == .claude ? [".font-user-message"] : []))','[]')
assert '\\(' not in v
esc={'n':'\n','r':'\r','t':'\t','0':'\0','"':'"','\\':'\\'}
v=re.sub(r'\\([nrt0"\\])',lambda m:esc[m[1]],v)
Path('/private/tmp/aibi-observer-expanded.js').write_text(v)
print('Expanded observer generated; provider values substituted with syntax-only literals.')
