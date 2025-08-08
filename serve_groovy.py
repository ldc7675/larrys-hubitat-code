from http.server import SimpleHTTPRequestHandler, HTTPServer
import os
import mimetypes

class PlainTextHandler(SimpleHTTPRequestHandler):
    def guess_type(self, path):
        # Force all files to be served as text/plain, no matter the extension
        return 'text/plain'

# Change directory to your driver folder
os.chdir("C:/govee_hubitat_drivers")

PORT = 8000
httpd = HTTPServer(("0.0.0.0", PORT), PlainTextHandler)
print(f"Serving on http://0.0.0.0:{PORT}")
httpd.serve_forever()
