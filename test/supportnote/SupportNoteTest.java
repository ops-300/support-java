package supportnote;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 外部のテストライブラリを使わず、重要な業務ルールと保存の失敗を再現する。 */
public class SupportNoteTest {
    private static int checks = 0;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("support-note-test-");
        try {
            Path file = directory.resolve("tickets.properties");
            TicketRepository repository = new TicketRepository(file);
            TicketService service = new TicketService(repository);
            expect(repository.findAll().isEmpty(), "初回は空");
            reject(() -> service.create("  ", "その他", "説明"), "空白件名を拒否");
            reject(() -> service.create("あ".repeat(81), "その他", "説明"), "長すぎる件名を拒否");
            reject(() -> service.create("件名", "不正分類", "説明"), "不正分類を拒否");
            reject(() -> service.create("件名", "その他", " "), "空白内容を拒否");
            expect(service.search("", "").isEmpty(), "不正入力は保存されない");
            Ticket ticket = service.create(" Wi-Fi不具合 ", "ネットワーク", "1行目\n2行目 &=日本語");
            expect(ticket.title.equals("Wi-Fi不具合"), "前後の空白を除去");
            expect(ticket.status == Ticket.Status.OPEN, "未対応で登録");
            service.update(ticket.id, "WORKING", "再起動を確認");
            expect(service.find(ticket.id).status == Ticket.Status.WORKING, "対応中に変更");
            reject(() -> service.update(ticket.id, "DONE", " "), "対応メモなしの完了を拒否");
            reject(() -> service.update(ticket.id, "INVALID", "説明"), "不正状態を拒否");
            expect(service.find(ticket.id).status == Ticket.Status.WORKING, "エラー時は状態を保持");
            service.update(ticket.id, "DONE", "接続先を修正\n動作確認済み");
            expect(service.search("接続先", "DONE").size() == 1, "対応メモを検索");
            expect(service.search("wi-fi", "").size() == 1, "英字の大文字小文字を無視");
            expect(service.search("", "OPEN").isEmpty(), "状況で絞り込み");
            TicketService reopened = new TicketService(new TicketRepository(file));
            expect(reopened.find(ticket.id).description.equals(ticket.description), "再読込で日本語・改行・記号を保持");
            expect(reopened.find(ticket.id).status == Ticket.Status.DONE, "再読込で完了状態を保持");
            expect(reopened.find(ticket.id).createdAt.equals(ticket.createdAt), "更新しても受付日時を保持");
            Ticket second = reopened.create("2件目", "その他", "説明");
            expect(second.id > ticket.id, "再起動後もIDが増える");
            // 意図: 指定しない場合は「中」、指定した場合はその優先度で登録されることを確認する。
            expect(second.priority == Ticket.Priority.NORMAL, "指定なしは中");

            Ticket high = reopened.create("高の確認", "その他", "確認用", "HIGH");
            Ticket low = reopened.create("低の確認", "その他", "確認用", "LOW");
            expect(high.priority == Ticket.Priority.HIGH, "高で登録");
            expect(low.priority == Ticket.Priority.LOW, "低で登録");

            // 意図: 選択肢にない優先度を保存しないことを確認する。
            reject(() -> reopened.create("不正値", "その他", "確認用", "INVALID"),
                "不正な優先度を拒否");

            // 意図: 対応状況を変えても、優先度が変わらないことを確認する。
            reopened.update(high.id, "WORKING", "確認中");
            expect(reopened.find(high.id).priority == Ticket.Priority.HIGH,
                "状態変更後も高を保持");

            // 意図: ファイルから読み直しても、低・高が復元されることを確認する。
            TicketService priorityReloaded =
                new TicketService(new TicketRepository(file));
            expect(priorityReloaded.find(high.id).priority == Ticket.Priority.HIGH,
                "再読込後も高を保持");
            expect(priorityReloaded.find(low.id).priority == Ticket.Priority.LOW,
                "再読込後も低を保持");
            List<Ticket> copy = repository.findAll();
            copy.clear();
            expect(!repository.findAll().isEmpty(), "一覧のコピーを変更しても内部データは変わらない");
            String escaped = HtmlViews.escape("<script>\"'&");
            expect(escaped.equals("&lt;script&gt;&quot;&#39;&amp;"), "HTMLの特殊文字を無害化");
            expect(WebController.parse("q=%E6%97%A5%E6%9C%AC%E8%AA%9E+test&empty=").get("q").equals("日本語 test"), "フォームの日本語を復元");
            Path corrupt = directory.resolve("corrupt.properties");
            Files.writeString(corrupt, "version=1\ncount=1\n");
            try { new TicketRepository(corrupt); throw new AssertionError("壊れた保存を拒否しなかった"); }
            catch (IOException expected) { checks++; }
            // 意図: 権限設定に依存しないよう、保存先をディレクトリでふさいでI/O失敗を起こす。
            Path blocked = directory.resolve("blocked.properties");
            TicketRepository blockedRepository = new TicketRepository(blocked);
            TicketService blockedService = new TicketService(blockedRepository);
            Files.createDirectory(blocked);
            Files.writeString(blocked.resolve("keep.txt"), "keep");
            try { blockedService.create("件名", "その他", "説明"); throw new AssertionError("保存失敗が起きなかった"); }
            catch (IOException expected) { checks++; }
            expect(blockedRepository.findAll().isEmpty(), "保存失敗時はメモリも更新されない");
            System.out.println("PASS: " + checks + " checks");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
    private static void expect(boolean actual, String name) {
        if (!actual) throw new AssertionError(name);
        checks++;
    }
    private static void reject(Action action, String name) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(name);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
