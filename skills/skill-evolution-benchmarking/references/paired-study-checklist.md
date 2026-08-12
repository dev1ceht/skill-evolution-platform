# Paired study checklist

## Before collection

- Define the claim and primary metric before seeing results.
- Pin repository baseline, contract/spec version, environment and completion checks.
- Pair the same task under traditional and Skill-assisted workflows.
- Choose alternating or randomized order and a common defect observation window.
- Define what pauses the timer and how interruptions are recorded.

## During collection

- Preserve task/PR/commit/timing references.
- Record failures and abandoned attempts; do not keep only successful tasks.
- Apply the same review and test gates to both sides.
- Label every row `real` or `synthetic` at creation time.
- Do not put sensitive personnel or customer data in a public repository.

## Before reporting

- Validate schema, unique IDs, positive durations and nonnegative counts.
- Verify source references and hash the exact input.
- Inspect paired ratios and total-weighted speedup; report P50/P90.
- Separate real and synthetic samples and retain excluded-row counts.
- State sample size, missing values, order effects, outliers and scope limits.
- Use `supported` only when the fixed domain threshold is satisfied.

