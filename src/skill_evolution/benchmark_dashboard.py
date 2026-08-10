from __future__ import annotations

import html
from typing import Any


def render_benchmark_dashboard(report: dict[str, Any]) -> str:
    duration = report["durationMinutes"]
    quality = report["quality"]
    claim = report["claim"]
    metric_scope = report["dataset"]["metricScope"]
    metric_label = "真实样本" if metric_scope == "real" else "合成演示样本"
    claim_label = {
        "supported": "达到可声明门槛",
        "not-supported": "样本充足但未达到 20×",
        "insufficient-real-samples": "真实样本不足，暂不可声明",
    }[claim["status"]]
    rows = "".join(
        "<tr>"
        f"<td>{html.escape(task['taskId'])}</td>"
        f"<td>{html.escape(task['taskName'])}</td>"
        f"<td>{task['traditionalMinutes']:.2f}</td>"
        f"<td>{task['skillMinutes']:.2f}</td>"
        f"<td>{task['traditionalMinutes'] / task['skillMinutes']:.2f}×</td>"
        f"<td>{html.escape(task['sampleType'])}</td>"
        "</tr>"
        for task in report["tasks"]
    )
    return f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>接口对接提效基准</title>
<style>
:root{{--ink:#17231b;--muted:#647269;--line:#d8e3da;--green:#128a53;--paper:#f5f8f5}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--paper);color:var(--ink);font:15px/1.6 system-ui,sans-serif}}
main{{max-width:1120px;margin:auto;padding:40px 24px}}h1{{font-size:36px;margin:0}}.sub{{color:var(--muted)}}
.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:16px;margin:28px 0}}
.card{{background:white;border:1px solid var(--line);border-radius:16px;padding:20px}}.value{{font-size:30px;font-weight:750;color:var(--green)}}
.status{{border-left:5px solid var(--green)}}table{{width:100%;border-collapse:collapse;background:white;border-radius:16px;overflow:hidden}}
th,td{{padding:12px 14px;border-bottom:1px solid var(--line);text-align:left}}th{{background:#eaf3ec}}small{{color:var(--muted)}}
</style></head><body><main>
<h1>接口对接提效基准</h1><p class="sub">同任务、同验收口径的传统开发与 Skill 驱动开发成对比较</p>
<section class="grid">
  <div class="card"><small>{metric_label}总耗时加权提速</small><div class="value">{duration['totalSpeedup']}×</div></div>
  <div class="card"><small>{metric_label} Skill 耗时 P50 / P90</small><div class="value">{duration['skillP50']} / {duration['skillP90']}</div></div>
  <div class="card"><small>首轮通过率（传统 → Skill）</small><div class="value">{quality['traditionalFirstPassRate']:.0%} → {quality['skillFirstPassRate']:.0%}</div></div>
  <div class="card status"><small>20× 证据状态</small><div>{html.escape(claim_label)}</div></div>
</section>
<table><thead><tr><th>任务</th><th>名称</th><th>传统分钟</th><th>Skill 分钟</th><th>提速</th><th>样本类型</th></tr></thead><tbody>{rows}</tbody></table>
<p><small>报告生成于 {html.escape(report['generatedAt'])}；输入 SHA-256：{html.escape(report['provenance']['inputSha256'])}</small></p>
</main></body></html>"""
