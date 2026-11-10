#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

python3 - <<'PY'
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path('.')

# Validate Maven XML.
ET.parse(root / 'pom.xml')
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
deps = ET.parse(root / 'pom.xml').findall('.//m:dependency', ns)
springdoc = [
    dep for dep in deps
    if dep.findtext('m:groupId', namespaces=ns) == 'org.springdoc'
    and dep.findtext('m:artifactId', namespaces=ns) == 'springdoc-openapi-starter-webmvc-ui'
]
if len(springdoc) != 1:
    raise SystemExit('Expected exactly one Springdoc WebMVC UI dependency in pom.xml.')
version = springdoc[0].findtext('m:version', namespaces=ns)
if version != '3.2.0':
    raise SystemExit(f'Expected Springdoc 3.2.0, found {version!r}.')

# Validate every checked-in Postman/OpenAPI JSON file.
json_files = sorted((root / 'postman').rglob('*.json'))
for path in json_files:
    json.loads(path.read_text(encoding='utf-8'))

spec = json.loads((root / 'postman/localcart-openapi.json').read_text(encoding='utf-8'))
if 'bearerAuth' not in spec.get('components', {}).get('securitySchemes', {}):
    raise SystemExit('OpenAPI snapshot is missing bearerAuth security scheme.')
public_paths = {
    '/api/test',
    '/api/payments/webhook/razorpay',
}
for path, operations in spec.get('paths', {}).items():
    is_public = path.startswith('/api/auth/') or path in public_paths
    for method, operation in operations.items():
        if method.lower() not in {'get', 'post', 'put', 'patch', 'delete', 'options', 'head', 'trace'}:
            continue
        security = operation.get('security')
        if is_public:
            if security != []:
                raise SystemExit(f'{method.upper()} {path} must be public in the OpenAPI snapshot.')
        else:
            if not isinstance(security, list) or not any('bearerAuth' in item for item in security):
                raise SystemExit(f'{method.upper()} {path} is missing bearerAuth in the OpenAPI snapshot.')

collection = json.loads((root / 'postman/collections/LocalCart-E2E.postman_collection.json').read_text(encoding='utf-8'))
if collection.get('auth', {}).get('type') != 'noauth':
    raise SystemExit('E2E collection must not set a shared bearer token; it switches identities during the workflow.')
if not any('localhost|127\\.0\\.0\\.1' in line and ':8081' in line for event in collection.get('event', []) for line in event.get('script', {}).get('exec', [])):
    raise SystemExit('E2E collection is missing its localhost:8081 safety guard.')

names = []
def walk(items):
    for item in items:
        if 'item' in item:
            walk(item['item'])
        else:
            names.append(item.get('name', ''))
walk(collection.get('item', []))
required_requests = {
    'Customer — capture cart quantity before add',
    'Customer — add item to cart',
    'Customer — verify cart',
    'Customer — checkout using platform delivery',
    'Customer — verify cart is empty after checkout',
}
missing = required_requests.difference(names)
if missing:
    raise SystemExit('E2E collection is missing: ' + ', '.join(sorted(missing)))

# Walk Postman request test scripts and ensure the immediate POST / GET assertions exist.
test_scripts = []
def collect_scripts(items):
    for item in items:
        if 'item' in item:
            collect_scripts(item['item'])
        else:
            test_scripts.extend(
                '\n'.join(event.get('script', {}).get('exec', []))
                for event in item.get('event', [])
                if event.get('listen') == 'test'
            )
collect_scripts(collection.get('item', []))
joined = '\n'.join(test_scripts)
for marker in ('Immediate POST response has the expected accumulated quantity',
               'GET quantity matches the immediate POST response',
               'Successful checkout clears all cart items'):
    if marker not in joined:
        raise SystemExit(f'E2E collection is missing assertion: {marker}')

for example_path in (root / 'postman/environments').glob('*.example.json'):
    example = json.loads(example_path.read_text(encoding='utf-8'))
    for value in example.get('values', []):
        if value.get('key', '').lower().endswith('password') or value.get('key', '').lower().endswith('token'):
            if value.get('value'):
                raise SystemExit(f'Environment example must not contain a password/token value: {example_path.name}:{value.get("key")}')

gitignore = (root / '.gitignore').read_text(encoding='utf-8')
for private_path in (
    'postman/environments/localcart.local.postman_environment.json',
    'postman/environments/localcart-api.local.postman_environment.json',
    'postman/reports/',
):
    if private_path not in gitignore:
        raise SystemExit(f'.gitignore is missing private/generated path: {private_path}')

api_collection = json.loads((root / 'postman/collections/localcart-api.postman_collection.json').read_text(encoding='utf-8'))
if api_collection.get('auth', {}).get('type') != 'bearer':
    raise SystemExit('Regular API collection must use bearer authentication for protected operations.')
login_requests = []
def collect_login_requests(items):
    for item in items:
        if 'item' in item:
            collect_login_requests(item['item'])
        elif item.get('name') == 'login':
            login_requests.append(item)
collect_login_requests(api_collection.get('item', []))
if not login_requests or not any(any('pm.environment.set("bearerToken", data.token)' in line for event in item.get('event', []) for line in event.get('script', {}).get('exec', [])) for item in login_requests):
    raise SystemExit('Regular API collection login must save its JWT to the private bearerToken environment variable.')

print(f'Phase 10 static verification passed ({len(json_files)} JSON files checked).')
PY

bash -n scripts/clone-localcart-test-db.sh
bash -n scripts/run-localcart-test-instance.sh
bash -n scripts/verify-phase10.sh

echo "Shell syntax checks passed."
