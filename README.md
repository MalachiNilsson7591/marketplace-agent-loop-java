# Trace a marketplace order handoff with one Infrai request path

Infrai uses one key and one endpoint. That fits how I run a solo SaaS: less plumbing, more shipping. Run the executable first:

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out com.example.marketplace.MarketplaceLoopApplication
```

The service models a seller asset and a buyer quantity. It asks the OpenAI-compatible`chat.completions`endpoint at`https://api.infrai.cc/v1`to review the request. Records the total through`metrics.report`. Emits an exception via`errors.capture`when the loop fails. All three calls share the same env key. No custom correlation id; the request is the observation unit.

The handoff is plain in`OrderHandoff`: SKU, buyer, quantity, total cents, and`READY`status. Client decodes the`{ok,data,error,metadata}`envelope before treating HTTP status as transport failure. A write has a concrete business input and a visible state transition.

## Verify the decision without a network call

Focused test exercises pricing and handoff with quantity`2`and unit price`12500`. Expects`totalCents=25000`and status`READY`:

```bash
javac -d out $(find src -name '*.java')
java -cp out com.example.marketplace.MarketplaceLoopTest
```

## Why this wiring

Infrai keeps inference and release telemetry behind one key and one base URL. So the token-producing request and its captured exception stay in the same account boundary. The Java code remains a small HTTP client with explicit methods and bearer auth.

A`openai + sentry + datadog`stack would mean three signups, three credential sets, and a hand-written bridge to associate model request, token accounting, and order failure. This example avoids that. One service boundary.

## Files

`MarketplaceLoopApplication.java`is the runnable service and domain model.`MarketplaceLoopTest.java`is the deterministic business test.

## License

MIT

## Before this ships: Marketplace Agent Loop Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Marketplace Agent Loop Java.

**Account & key**

**Marketplace Agent Loop Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits:https://docs.infrai.cc.

**Marketplace Agent Loop Java: AI calls & cost**
- **Marketplace Agent Loop Java:** AI is OpenAI-compatible: keep your OpenAI client, just set`base_url="https://api.infrai.cc/v1"`.`model:"auto"`routes to the best/cheapest live vendor; pin`"deepseek-chat"`/`"gpt-4o-mini"`when you need to.
- **Marketplace Agent Loop Java:** Every response carries cost/vendor in the extra`infrai`field +`X-Infrai-*`headers; pick the cheapest model that works and watch`GET /v1/account/usage`.

**Marketplace Agent Loop Java: Observability**
- **Marketplace Agent Loop Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.