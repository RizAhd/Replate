/*
  Tiny test harness for OTP functions.
  Usage:
    node test-client.js https://<region>-<project-id>.cloudfunctions.net your@email.com
*/

const baseUrl = process.argv[2];
const email = process.argv[3] || "test@example.com";

if (!baseUrl) {
  console.error("Missing base URL");
  process.exit(1);
}

async function post(path, body) {
  const res = await fetch(`${baseUrl}/${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = await res.json();
  return { status: res.status, json };
}

(async () => {
  const send = await post("sendOtpEmail", { email, name: "CLI Test" });
  console.log("sendOtpEmail", send.status, send.json);

  console.log("Now check your email and call verify manually with your OTP.");
  console.log("Example:");
  console.log(`curl -X POST ${baseUrl}/verifyOtp -H \"Content-Type: application/json\" -d '{\"email\":\"${email}\",\"otp\":\"123456\"}'`);
})();

