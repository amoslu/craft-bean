# deploy

开发期本地数据库：
1. `cp .env.example .env`
2. `docker compose up -d mysql`
3. 验证：`docker exec craftbean-mysql mysqladmin ping -uroot -proot123`

S6 上线时在此目录追加 nginx + server 服务（见技术设计 §3）。
