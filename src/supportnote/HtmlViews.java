package supportnote;

import java.time.format.DateTimeFormatter;
import java.util.*;

/** HTMLを作る担当。入力された文字列は必ずescapeしてから表示する。 */
public class HtmlViews {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    public static String escape(String value) {
        // 意図: 入力をHTMLとして実行させず、単なる文字として表示する（XSS対策）。
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String page(String title, String body) {
        return """
            <!doctype html><html lang="ja"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s | Support Note</title><link rel="stylesheet" href="/style.css"></head>
            <body><header><a class="brand" href="/"><span class="brand-icon">S</span>Support Note</a>
            <span class="header-label">問い合わせ管理</span><a class="button" href="/new">＋ 新規登録</a></header>
            <main>%s</main><footer>Support Note · Java学習ポートフォリオ / 個人用ローカルアプリ</footer></body></html>
            """.formatted(escape(title), body);
    }

    private static String statusOptions(String selected, boolean all) {
        StringBuilder out = new StringBuilder();
        if (all) out.append("<option value=\"\">すべての状況</option>");
        for (Ticket.Status status : Ticket.Status.values()) {
            out.append("<option value=\"").append(status.name()).append("\"")
                .append(status.name().equals(selected) ? " selected" : "")
                .append(">").append(status.label).append("</option>");
        }
        return out.toString();
    }

    private static String priorityOptions(String selected) {
        // 意図: enumから選択肢を作り、値と表示名の二重管理を避ける。
        // 意図: 入力エラーで画面に戻った場合も、選ばれた優先度を保持する。
        StringBuilder out = new StringBuilder();
        for (Ticket.Priority priority : Ticket.Priority.values()) {
            out.append("<option value=\"").append(priority.name()).append("\"")
                .append(priority.name().equals(selected) ? " selected" : "")
                .append(">").append(escape(priority.label)).append("</option>");
        }
        return out.toString();
    }

    private static String notice(String error) {
        return error.isEmpty() ? "" : "<div class=\"notice error\" role=\"alert\">" + escape(error) + "</div>";
    }

    private static String token(String token) {
        return "<input type=\"hidden\" name=\"token\" value=\"" + escape(token) + "\">";
    }

    public static String list(List<Ticket> shown, List<Ticket> all, String q, String status, boolean saved) {
        int open = 0, working = 0, done = 0;
        for (Ticket t : all) {
            if (t.status == Ticket.Status.OPEN) open++;
            else if (t.status == Ticket.Status.WORKING) working++;
            else done++;
        }
        StringBuilder rows = new StringBuilder();
        for (Ticket t : shown) {
            rows.append("<a class=\"ticket-row\" href=\"/ticket?id=").append(t.id).append("\">")
                .append("<span class=\"ticket-id\">#").append(String.format("%03d", t.id)).append("</span>")
                .append("<div class=\"ticket-main\"><strong>").append(escape(t.title)).append("</strong>")
                .append("<span>").append(escape(t.category)).append(" · ").append(t.priority.label).append(" · ").append(t.createdAt.format(DATE)).append("</span></div>")
                .append("<span class=\"badge ").append(t.status.name()).append("\">").append(t.status.label)
                .append("</span><span class=\"arrow\" aria-hidden=\"true\">→</span></a>");
        }
        if (shown.isEmpty()) rows.append("<div class=\"empty\"><h3>該当する問い合わせはありません</h3><p>新しく登録するか、検索条件を変更してください。</p><a href=\"/new\">問い合わせを登録する →</a></div>");
        String body = """
            <div class="eyebrow">SUPPORT WORKSPACE</div><h1>問い合わせを、見失わない。</h1>
            <p class="lead">受付から解決までを記録。過去の対応を、次の助けに。</p>
            %s
            <section class="stats" aria-label="全件の対応状況">
              <div class="stat"><span>未対応</span><strong>%d<small>件</small></strong><p>これから対応する問い合わせ</p></div>
              <div class="stat"><span>対応中</span><strong>%d<small>件</small></strong><p>確認・調査を進めているもの</p></div>
              <div class="stat"><span>完了</span><strong>%d<small>件</small></strong><p>対応メモを残して解決</p></div>
            </section>
            <section class="panel"><div class="section-title"><h2>問い合わせ一覧</h2><span>%d / %d件</span></div>
            <form class="filters" method="get" action="/">
            <label class="search-label">キーワード<input name="q" maxlength="200" value="%s" placeholder="件名・内容・対応メモから検索"></label>
            <label>対応状況<select name="status">%s</select></label><button>検索</button><a class="reset" href="/">解除</a></form>
            <div class="ticket-list">%s</div></section>
            """.formatted(saved ? "<div class=\"notice\" role=\"status\">保存しました。</div>" : "",
                open, working, done, shown.size(), all.size(), escape(q), statusOptions(status, true), rows);
        return page("問い合わせ一覧", body);
    }

    public static String create(Map<String, String> values, String error, String csrf) {
        StringBuilder options = new StringBuilder("<option value=\"\">選んでください</option>");
        for (String category : TicketService.CATEGORIES) {
            options.append("<option").append(category.equals(values.get("category")) ? " selected" : "")
                .append(">").append(escape(category)).append("</option>");
        }
        return page("新規登録", """
            <a class="back" href="/">← 一覧へ戻る</a><div class="eyebrow">NEW TICKET</div>
            <h1>問い合わせを登録</h1><p class="lead">困っていることを、あとで確認できる形に。</p>%s
            <section class="panel form-panel"><form method="post" action="/create">%s
            <label>件名 <span class="required">必須</span><input name="title" maxlength="80" required value="%s" placeholder="例：社内Wi-Fiに接続できない"></label>
            <label>分類 <span class="required">必須</span><select name="category" required>%s</select></label>
            <label>優先度
                <select name="priority" required>%s</select>
            </label>
            <label>内容 <span class="required">必須</span><textarea name="description" maxlength="2000" required rows="7" placeholder="発生した状況や、すでに試したことを書いてください。">%s</textarea></label>
            <p class="hint">件名80文字・内容2,000文字まで。未対応として登録します。</p>
            <div class="form-actions"><button>問い合わせを登録</button><a href="/">キャンセル</a></div>
            </form></section>
            """.formatted(notice(error), token(csrf), escape(values.getOrDefault("title", "")), options,
                priorityOptions(values.getOrDefault("priority", "NORMAL")),
                escape(values.getOrDefault("description", ""))));
    }

    public static String detail(Ticket t, Map<String, String> values, String error, String csrf) {
        return page(t.title, """
            <a class="back" href="/">← 一覧へ戻る</a><div class="eyebrow">TICKET #%03d</div>
            <h1>%s</h1><p class="lead">%s · 受付 %s · 更新 %s</p>%s
            <div class="detail-grid"><section class="panel"><h2>問い合わせ内容</h2><p class="multiline">%s</p></section>
            <section class="panel"><h2>対応を記録</h2><form method="post" action="/update">%s
            <input type="hidden" name="id" value="%d"><label>対応状況<select name="status">%s</select></label>
            <label>対応メモ<textarea name="resolution" maxlength="2000" rows="8" placeholder="確認したこと・実施した対応・結果を記録">%s</textarea></label>
            <p class="hint">完了にするには対応メモが必要です。2,000文字まで。</p><button>変更を保存</button></form></section></div>
            """.formatted(t.id, escape(t.title), escape(t.category), t.createdAt.format(DATE), t.updatedAt.format(DATE),
                notice(error), escape(t.description), token(csrf), t.id,
                statusOptions(values.getOrDefault("status", t.status.name()), false),
                escape(values.getOrDefault("resolution", t.resolution))));
    }

    public static String error(String message) {
        return page("エラー", "<section class=\"panel\"><h1>操作を完了できませんでした</h1>" + notice(message) + "<a href=\"/\">一覧へ戻る</a></section>");
    }
}
