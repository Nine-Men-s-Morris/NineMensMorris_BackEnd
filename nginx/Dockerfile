FROM nginx:latest

# 기존 Nginx 설정 파일 제거
RUN rm /etc/nginx/nginx.conf

# 새로운 설정 파일을 복사
COPY nginx/nginx.conf /etc/nginx/nginx.conf
