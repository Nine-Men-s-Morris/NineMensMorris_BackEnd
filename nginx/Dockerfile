FROM nginx:latest

# 가상 공간의 nginx 기본 설정 파일 제거
RUN rm /etc/nginx/conf.d/nginx/conf

# 작성한 파일로 교체
COPY nginx.conf /etc/nginx/conf.d