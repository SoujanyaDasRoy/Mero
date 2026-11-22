#!/usr/bin/env bash
# Checks the group-playlist endpoints against the live site (or H=<url>).
# Usage: bash test-groups.sh     Exits non-zero at the first failed check.
set -u
H="${H:-https://meroapp.pages.dev}"
A=dQw4w9WgXcQ; B=kJQP7kiw5Fk; C=9bZkp7q19f0
TMP="$(mktemp -d)"
CREATED=""   # group codes made by this run, deleted at the end
# Test groups are not kept (real ones are never deleted): removed on exit, pass or fail.
cleanup() {
  codes=$(for c in $CREATED; do [ ${#c} = 8 ] && printf "'%s'," "$c"; done)
  [ -n "$codes" ] && command -v wrangler >/dev/null || return 0
  ( cd "$(dirname "$0")" && wrangler d1 execute mero --remote -y --command "DELETE FROM steps WHERE code IN (${codes%,}); DELETE FROM groups WHERE code IN (${codes%,});" >/dev/null 2>&1 ) || echo "(could not delete test groups)"
  for c in $CREATED; do ( cd "$(dirname "$0")" && wrangler kv key delete "g:$c" --binding SHARES --remote >/dev/null 2>&1 ); done
}
trap cleanup EXIT
curl() { command curl -4 --retry 2 --retry-all-errors "$@"; }
fail() { echo "FAIL: $*"; exit 1; }
post() { printf '%s' "$2" > "$TMP/body.json"; curl -s -w '\n%{http_code}' -X POST "$H$1" -H 'content-type: application/json' --data-binary @"$TMP/body.json"; }
body() { printf '%s' "$1" | sed '$d'; }
status() { printf '%s' "$1" | tail -n1; }
field() { printf '%s' "$1" | sed -n "s/.*\"$2\":\([^,}]*\).*/\1/p" | tr -d '"'; }

# 1. create
r=$(post /api/g "{\"name\":\"T\",\"ids\":[\"$A\",\"$B\"]}")
[ "$(status "$r")" = 200 ] || fail "create: $(status "$r") $(body "$r")"
code=$(field "$(body "$r")" code); head=$(field "$(body "$r")" head); CREATED="$CREATED $code"
[ ${#code} = 8 ] || fail "create: code '$code'"
[ "$head" = 2 ] || fail "create: head '$head'"

# 2. read everything
g=$(curl -s "$H/api/g/$code?since=0")
echo "$g" | grep -q "\"seq\":1,\"kind\":\"add\",\"value\":\"$A\"" || fail "since=0 step 1: $g"
echo "$g" | grep -q "\"seq\":2,\"kind\":\"add\",\"value\":\"$B\"" || fail "since=0 step 2: $g"
echo "$g" | grep -q '"more":true' && fail "since=0 says more: $g"

# 3. append
r=$(post "/api/g/$code/steps" "{\"steps\":[{\"opId\":\"x1\",\"kind\":\"remove\",\"value\":\"$A\"},{\"opId\":\"x2\",\"kind\":\"add\",\"value\":\"$C\"}]}")
[ "$(field "$(body "$r")" head)" = 4 ] || fail "append: $(body "$r")"

# 4. repeat op_id is skipped
r=$(post "/api/g/$code/steps" "{\"steps\":[{\"opId\":\"x1\",\"kind\":\"remove\",\"value\":\"$A\"}]}")
[ "$(field "$(body "$r")" head)" = 4 ] || fail "repeat op_id: $(body "$r")"

# 5. since
g=$(curl -s "$H/api/g/$code?since=2")
echo "$g" | grep -q '"seq":3' && echo "$g" | grep -q '"seq":4' || fail "since=2: $g"
echo "$g" | grep -q '"seq":2' && fail "since=2 includes 2: $g"

# 6. bad input -> 400
[ "$(status "$(post /api/g '{"name":"T","ids":["bad"]}')")" = 400 ] || fail "bad id accepted"
[ "$(status "$(post "/api/g/$code/steps" "{\"steps\":[{\"opId\":\"y\",\"kind\":\"drop\",\"value\":\"$A\"}]}")")" = 400 ] || fail "bad kind accepted"
long=$(printf 'n%.0s' $(seq 1 81))
[ "$(status "$(post /api/g "{\"name\":\"$long\",\"ids\":[\"$A\"]}")")" = 400 ] || fail "81-char name accepted"
many=$(for i in $(seq 1 1001); do printf '{"opId":"m%s","kind":"add","value":"%s"},' "$i" "$A"; done)
[ "$(status "$(post "/api/g/$code/steps" "{\"steps\":[${many%,}]}")")" = 400 ] || fail "1001 steps accepted"

# 7. unknown group
[ "$(curl -s -o /dev/null -w '%{http_code}' "$H/api/g/AAAAAAAA?since=0")" = 404 ] || fail "unknown group not 404"

# 8. cover
printf 'abc' > "$TMP/bad.jpg"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$H/api/g/$code/cover" --data-binary @"$TMP/bad.jpg")" = 400 ] || fail "non-JPEG cover accepted"
curl -s -o "$TMP/ok.jpg" "https://i.ytimg.com/vi/$A/default.jpg"
r=$(curl -s -w '\n%{http_code}' -X PUT "$H/api/g/$code/cover" -H 'content-type: image/jpeg' --data-binary @"$TMP/ok.jpg")
[ "$(status "$r")" = 200 ] && [ "$(field "$(body "$r")" head)" = 5 ] || fail "cover: $(body "$r")"
[ "$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$H/c/g/$code.jpg")" = "200 image/jpeg" ] || fail "cover not served"
curl -s "$H/api/g/$code?since=4" | grep -q '"kind":"cover"' || fail "no cover step"

# 9. preview page
curl -s "$H/g/$code" | grep -q 'og:title" content="T"' || fail "preview page"

# 10. paging past 1000
ids=$(for i in $(seq 1 1000); do printf '"%s",' "$(printf 'p%010d' "$i")"; done)
r=$(post /api/g "{\"name\":\"Big\",\"ids\":[${ids%,}]}")
big=$(field "$(body "$r")" code); CREATED="$CREATED $big"
[ "$(field "$(body "$r")" head)" = 1000 ] || fail "big create: $(body "$r")"
more=$(for i in $(seq 1 500); do printf '{"opId":"q%s","kind":"add","value":"%s"},' "$i" "$(printf 'q%010d' "$i")"; done)
r=$(post "/api/g/$big/steps" "{\"steps\":[${more%,}]}")
[ "$(field "$(body "$r")" head)" = 1500 ] || fail "big append: $(body "$r")"
g=$(curl -s "$H/api/g/$big?since=0")
[ "$(echo "$g" | grep -o '"seq":' | wc -l)" = 1000 ] && echo "$g" | grep -q '"more":true' || fail "first page"
g=$(curl -s "$H/api/g/$big?since=1000")
[ "$(echo "$g" | grep -o '"seq":' | wc -l)" = 500 ] && ! echo "$g" | grep -q '"more":true' || fail "second page"

# 11. a body sent in chunks (no content-length) is still size-checked
pad=$(yes x | head -n 300000 | tr -d '\n')
printf '{"steps":[{"opId":"z1","kind":"add","value":"%s"}],"pad":"%s"}' "$A" "$pad" > "$TMP/huge.json"
st=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$H/api/g/$code/steps" -H 'content-type: application/json' -H 'Transfer-Encoding: chunked' --data-binary @"$TMP/huge.json")
[ "$st" = 413 ] || fail "chunked 300 KB body: $st (want 413)"

# 12. (RATE=1 only) too many new groups from one sender in an hour gets 429. Uses up this hour's allowance.
if [ "${RATE:-0}" = 1 ]; then
  got=""
  for i in $(seq 1 35); do
    r=$(post /api/g "{\"name\":\"Rate $i\",\"ids\":[\"$A\"]}")
    if [ "$(status "$r")" = 429 ]; then got=yes; break; fi
    CREATED="$CREATED $(field "$(body "$r")" code)"
  done
  [ "$got" = yes ] || fail "no 429 after 35 new groups"
fi

echo "all group checks passed"
