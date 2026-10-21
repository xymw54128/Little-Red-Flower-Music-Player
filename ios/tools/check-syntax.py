from pathlib import Path
import json, hashlib
import importlib.metadata
from tree_sitter import Language, Parser
import tree_sitter_swift

root = Path(__file__).resolve().parents[1]
parser = Parser(Language(tree_sitter_swift.language()))
errors = []
checked = []
for path in sorted(root.rglob('*.swift')):
    if any(part in ['.build', 'DerivedData'] for part in path.parts):
        continue
    data = path.read_bytes()
    tree = parser.parse(data)
    checked.append(str(path.relative_to(root)).replace('\\', '/'))
    if tree.root_node.has_error:
        def visit(node):
            if node.type == 'ERROR' or node.is_missing:
                row, col = node.start_point
                errors.append({'file': str(path.relative_to(root)), 'line': row + 1, 'column': col + 1, 'kind': node.type, 'text': data[node.start_byte:node.end_byte].decode('utf-8')[:180]})
            else:
                for child in node.children:
                    if child.has_error:
                        visit(child)
        visit(tree.root_node)
report = {'scope': 'Swift grammar parsing only, not type checking or Apple SDK compilation',
          'treeSitter': importlib.metadata.version('tree-sitter'), 'swiftGrammar': importlib.metadata.version('tree-sitter-swift'),
          'files': checked, 'errors': errors,
          'sourceSha256': {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in checked}}
(root / 'verification').mkdir(exist_ok=True)
(root / 'verification/SWIFT-SYNTAX.json').write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
print(json.dumps(report, ensure_ascii=False, indent=2))
raise SystemExit(1 if errors else 0)
