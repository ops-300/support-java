package supportnote;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/** 入力チェックや対応ルールを担当する。HTTPやファイル形式を知らなくても読めるようにする。 */
public class TicketService {
    public static final List<String> CATEGORIES = List.of("PC・周辺機器", "アカウント", "ネットワーク", "その他");
    private final TicketRepository repository;

    public TicketService(TicketRepository repository) { this.repository = repository; }

    public List<Ticket> search(String keyword, String status) {
        String query = keyword.strip().toLowerCase(Locale.ROOT);
        if (!status.isEmpty()) parseStatus(status);
        List<Ticket> result = new ArrayList<>();
        for (Ticket ticket : repository.findAll()) {
            // 意図: 過去の対処方法も探せるよう、件名・本文・対応メモを検索対象にする。
            String text = (ticket.title + " " + ticket.description + " " + ticket.resolution).toLowerCase(Locale.ROOT);
            if (text.contains(query) && (status.isEmpty() || ticket.status.name().equals(status))) result.add(ticket);
        }
        result.sort(Comparator.comparingLong((Ticket t) -> t.id).reversed());
        return result;
    }

    public Ticket find(long id) {
        for (Ticket t : repository.findAll()) if (t.id == id) return t;
        throw new NoSuchElementException("問い合わせが見つかりません。");
    }

    public synchronized Ticket create(String title, String category, String description, String priority) throws IOException {
        Ticket.Priority selectedPriority;
        try { selectedPriority = Ticket.Priority.valueOf(priority); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("優先度の値が不正です。"); }
        title = checkedText(title, "件名", 80, true);
        description = checkedText(description, "内容", 2000, true);
        if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("分類を選んでください。");
        List<Ticket> next = repository.findAll();
        long id = 1;
        for (Ticket t : next) id = Math.max(id, Math.addExact(t.id, 1));
        LocalDateTime now = LocalDateTime.now();
        // 意図: 新規登録は必ず未対応から始め、優先度は選ばれた値を設定する。
        Ticket ticket = new Ticket(id, title, category, description, Ticket.Status.OPEN, selectedPriority, "", now, now);
        next.add(ticket);
        repository.saveAll(next);
        return ticket;
    }

    // 意図: 優先度を指定しない呼び出しでは「中」を使う。
    public Ticket create(String title, String category, String description) throws IOException {
        return create(title, category, description, "NORMAL");
    }

    public synchronized void update(long id, String status, String resolution) throws IOException {
        Ticket.Status selected = parseStatus(status);
        resolution = checkedText(resolution, "対応メモ", 2000, selected == Ticket.Status.DONE);
        // 意図: 完了の根拠を残すため、完了にするときだけ対応メモを必須にする。
        List<Ticket> next = repository.findAll();
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id == id) {
                next.set(i, next.get(i).withProgress(selected, resolution));
                repository.saveAll(next);
                return;
            }
        }
        throw new NoSuchElementException("問い合わせが見つかりません。");
    }

    private static String checkedText(String value, String label, int max, boolean required) {
        String cleaned = value.strip();
        if (required && cleaned.isEmpty()) throw new IllegalArgumentException(label + "を入力してください。");
        if (cleaned.codePointCount(0, cleaned.length()) > max) throw new IllegalArgumentException(label + "は" + max + "文字以内で入力してください。");
        return cleaned;
    }

    private static Ticket.Status parseStatus(String value) {
        try { return Ticket.Status.valueOf(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("対応状況の値が不正です。"); }
    }
}
