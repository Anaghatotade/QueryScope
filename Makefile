.PHONY: up down logs test api-demo

up:
	docker compose up --build -d

down:
	docker compose down -v

logs:
	docker compose logs -f backend

test:
	cd backend && mvn test

api-demo:
	curl -s -X POST http://localhost:8080/api/analyze \
	  -H 'Content-Type: application/json' \
	  -d '{"sql":"SELECT * FROM orders WHERE customer_id = 123 AND created_at > '\''2026-01-01'\''"}' | python3 -m json.tool
