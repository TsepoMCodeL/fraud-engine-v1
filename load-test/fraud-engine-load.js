import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const testRunId = __ENV.TEST_RUN_ID || 'local';

export const options = {
  stages: [
    { duration: '15s', target: 2 },
    { duration: '30s', target: 5 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
  tags: {
    test_run: testRunId,
  },
};

export default function () {
  const transactionId = `LOAD-${testRunId}-${__VU}-${__ITER}`;
  const request = {
    transactionId,
    accountId: `LOAD-ACCOUNT-${testRunId}-${__VU}`,
    amount: 45.5,
    currency: 'ZAR',
    timestamp: new Date().toISOString(),
    merchantId: 'LOAD-TEST-MERCHANT',
    merchantCategoryCode: '5411',
    ipAddress: '192.0.2.10',
    deviceId: `LOAD-DEVICE-${__VU}`,
    metadata: { source: 'k6-load-test' },
  };

  const response = http.post(
    `${baseUrl}/api/v1/fraud/evaluate`,
    JSON.stringify(request),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(response, {
    'evaluation returns HTTP 200': (res) => res.status === 200,
    'evaluation response contains a decision': (res) => {
      if (res.status !== 200) {
        return false;
      }
      try {
        const body = res.json();
        return body.transactionId === transactionId &&
          ['APPROVE', 'REVIEW', 'DECLINE'].includes(body.action);
      } catch (_) {
        return false;
      }
    },
  });

  sleep(0.2);
}
