"""Read-only Azure Blob HTTP fixture for native-image smoke tests, not a production provider."""
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path('/data').resolve()
PREFIX = '/testaccount/reference-data/'


class Handler(BaseHTTPRequestHandler):
    def do_HEAD(self):
        self.serve(False)

    def do_GET(self):
        self.serve(True)

    def serve(self, body):
        path = unquote(urlsplit(self.path).path)
        if path == '/health':
            self.send_response(200)
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        if not path.startswith(PREFIX):
            self.send_error(404)
            return
        file = (ROOT / path[len(PREFIX):]).resolve()
        if not file.is_relative_to(ROOT) or not file.is_file():
            self.send_error(404)
            return
        data = file.read_bytes()
        etag = '"' + sha256(data).hexdigest() + '"'
        # Azure's SDK normalizes ETag quotes before sending If-Match.
        if self.headers.get('If-Match', etag).strip('"') != etag.strip('"'):
            self.send_error(412)
            return
        self.send_response(200)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Content-Type', 'application/json')
        self.send_header('ETag', etag)
        self.send_header('Last-Modified', 'Sat, 14 Nov 2026 12:00:00 GMT')
        self.send_header('x-ms-blob-type', 'BlockBlob')
        self.send_header('x-ms-request-id', '00000000-0000-0000-0000-000000000001')
        self.end_headers()
        if body:
            self.wfile.write(data)

    def log_message(self, *_):
        pass


ThreadingHTTPServer(('0.0.0.0', 10000), Handler).serve_forever()
