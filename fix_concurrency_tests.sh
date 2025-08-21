#!/bin/bash

# 남은 동시성 테스트 파일들을 수정하는 스크립트

FILES=(
    "C:/Users/dksdudwns/OneDrive/Desktop/server-kotlin/server-kotlin/src/test/kotlin/kr/hhplus/be/server/api/payment/concurrency/PaymentConcurrencyTest.kt"
    "C:/Users/dksdudwns/OneDrive/Desktop/server-kotlin/server-kotlin/src/test/kotlin/kr/hhplus/be/server/api/reservation/concurrency/ReservationConcurrencyTest.kt"
    "C:/Users/dksdudwns/OneDrive/Desktop/server-kotlin/server-kotlin/src/test/kotlin/kr/hhplus/be/server/domain/auth/service/QueueManagerConcurrencyTest.kt"
)

echo "Remaining concurrency test files to modify:"
for file in "${FILES[@]}"; do
    echo "$file"
done
