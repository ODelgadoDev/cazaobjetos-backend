#!/usr/bin/env python3
"""HTTP checks against a running dev instance. Creates fictitious records; never deletes."""
import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get('API_URL', 'http://127.0.0.1:8080') + '/api/v1'
PASSWORD = os.environ['DEV_PASSWORD']

class Client:
    def __init__(self):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None

    def call(self, method, path, payload=None, expected=200, csrf=True, form=False):
        headers = {}
        data = None
        if payload is not None:
            data = (urllib.parse.urlencode(payload) if form else json.dumps(payload)).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        if csrf and self.csrf:
            headers[self.csrf['headerName']] = self.csrf['token']
        req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            response = self.http.open(req)
        except urllib.error.HTTPError as exc:
            response = exc
        body = response.read()
        assert response.code == expected, (method, path, response.code, body)
        return json.loads(body) if body else None

    def login(self, name):
        self.csrf = self.call('GET', '/auth/csrf')
        self.call('POST', '/auth/login', {'username': name + '@utch.edu.mx', 'password': PASSWORD}, expected=204, form=True)
        self.csrf = self.call('GET', '/auth/csrf')

anonymous = Client()
anonymous.call('GET', '/found-items', expected=401)
finder, owner, stranger = Client(), Client(), Client()
finder.login('alumno.demo')
profile = finder.call('GET', '/auth/me')
assert profile['email'] == 'alumno.demo@utch.edu.mx'
assert 'displayName' in profile and 'passwordHash' not in profile
owner.login('reclamante.demo')
stranger.login('tercero.demo')
report = {'title': 'Prueba HTTP mochila', 'description': 'Registro ficticio', 'category': 'OTHER', 'location': 'Edificio I', 'occurredOn': '2026-10-30'}
finder.call('POST', '/found-items', report, expected=403, csrf=False)
finder.call('POST', '/found-items', {**report, 'title': ''}, expected=400)
item = finder.call('POST', '/found-items', report, expected=201)['id']
stranger.call('POST', f'/found-items/{item}/office', expected=403)
finder.call('POST', f'/found-items/{item}/office', expected=204)
view = finder.call('GET', f'/found-items/{item}')
assert view['custody'] == 'OFFICE' and view['officeMarkedBy'] == profile['id']
assert view['responsibleId'] == profile['id']
claim = owner.call('POST', f'/found-items/{item}/claims', {'privateDescription': 'Mi cuaderno está dentro'}, expected=201)['id']
assert not stranger.call('GET', '/claims')
owner.call('POST', f'/claims/{claim}/complete', expected=409)
stranger.call('POST', f'/claims/{claim}/accept', expected=403)
finder.call('POST', f'/claims/{claim}/accept', expected=204)
owner.call('POST', f'/claims/{claim}/complete', expected=204)
owner.call('POST', f'/claims/{claim}/complete', expected=204)
assert finder.call('GET', f'/found-items/{item}')['status'] == 'RETURNED'
owner.call('POST', '/auth/logout', expected=204)
owner.call('GET', '/auth/me', expected=401)
print('HTTP OK: login, CSRF, validación, permisos, privacidad, aceptación, recuperación, reintento y logout.')
