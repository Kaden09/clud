#!/usr/bin/env python3
"""Assert the error contract against the default Compose ports (uses an isolated smoke user)."""
import argparse
import json
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def send(base, path, method="GET", data=None, token=None, content_type="application/json"):
    headers = {"Content-Type": content_type} if data is not None else {}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(base + path, data=data, method=method, headers=headers)
    try:
        response = urlopen(request, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        body = response.read()
        return response.status, json.loads(body) if body else None, response.headers


def check(base, path, status, code, *, expected_path=None, fields=False, **kwargs):
    actual, body, headers = send(base, path, **kwargs)
    assert actual == status, (base, path, actual, body)
    assert body["status"] == status and body["code"] == code, body
    assert body["path"] == (expected_path or path), body
    assert body["timestamp"] and body["message"], body
    assert headers.get_content_type() == "application/json", headers
    assert bool(body.get("fieldErrors")) if fields else "fieldErrors" not in body, body
    return body, headers


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream-down", action="store_true")
    args = parser.parse_args()
    if args.upstream_down:
        for base in ["http://localhost:8080", "http://localhost"]:
            check(base, "/api/identity/auth/login", 502, "BAD_GATEWAY", method="POST", data=b"{}")
        print("Gateway and Nginx upstream connection failure checks passed")
        return

    for port in range(8080, 8085):
        base = f"http://localhost:{port}"
        for path in ["/", "/unknown-endpoint"]:
            check(base, path, 404, "NOT_FOUND")
    check("http://localhost", "/", 404, "NOT_FOUND")
    check("http://localhost:8081", "/user/me", 401, "UNAUTHORIZED")
    check("http://localhost:8080", "/api/files/nodes", 401, "UNAUTHORIZED")

    for base, prefix in [("http://localhost:8081", ""), ("http://localhost:8080", "/api/identity"),
                         ("http://localhost", "/api/identity")]:
        check(base, prefix + "/auth/login", 400, "BAD_REQUEST", method="POST", data=b"{",
              expected_path="/auth/login")
        _, headers = check(base, prefix + "/auth/login", 405, "METHOD_NOT_ALLOWED", expected_path="/auth/login")
        assert "POST" in headers["Allow"], headers
        check(base, prefix + "/auth/register", 400, "BAD_REQUEST", method="POST",
              data=b'{"email":"invalid","password":"short"}', expected_path="/auth/register", fields=True)

    credentials = json.dumps({"email": f"errors-{uuid.uuid4()}@example.com", "password": "Password123"}).encode()
    identity = "http://localhost:8081"
    status, _, _ = send(identity, "/auth/register", "POST", credentials)
    assert status == 201, status
    status, login, _ = send(identity, "/auth/login", "POST", credentials)
    assert status == 200, (status, login)
    token = login["accessToken"]
    for base in ["http://localhost:8080", "http://localhost"]:
        for service in ["identity", "files", "sharing"]:
            check(base, f"/api/{service}/unknown-endpoint", 404, "NOT_FOUND",
                  expected_path="/unknown-endpoint", token=token)
        check(base, "/api/files/internal/nodes/1", 403, "FORBIDDEN", token=token)
    print("Direct service, Gateway, and Nginx error contract checks passed")


if __name__ == "__main__":
    main()
