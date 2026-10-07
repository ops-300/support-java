package supportnote;

import java.time.LocalDateTime;

/** 問い合わせ1件のデータ。画面や保存処理を入れず、データの意味を表す役割に絞る。 */
public class Ticket {
    // 意図: 状態を自由な文字列にすると「完了」「対応済」などが混ざるため、選択肢を固定する。
    public enum Status {
        OPEN("未対応"), WORKING("対応中"), DONE("完了");

        public final String label;

        Status(String label) {
            this.label = label;
        }
    }

    // 意図: 優先度を自由な文字列にすると「低」「中」「高」以外の値が混ざるため、選択肢を固定する。
    public enum Priority {
        LOW("低"), NORMAL("中"), HIGH("高");

        public final String label;

        Priority(String label) {
            this.label = label;
        }
    }

    // 意図: 不変のデータにして、保存せずに一覧だけ書き換わる事故を避ける。
    public final long id;
    public final String title;
    public final String category;
    public final String description;
    public final Status status;
    // 意図: 問い合わせごとに優先度を保持し、対応順の判断に使う。
    public final Priority priority;
    public final String resolution;
    public final LocalDateTime createdAt;
    public final LocalDateTime updatedAt;

    public Ticket(long id, String title, String category, String description,
                  Status status, Priority priority, String resolution, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.description = description;
        this.status = status;
        // 意図: 受け取った優先度を、この問い合わせ項目に設定する。
        this.priority = priority;
        this.resolution = resolution;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Ticket withProgress(Status status, String resolution) {
        // 意図: IDと受付日時を保った新しいデータを作り、更新箇所を明確にする。
        return new Ticket(id, title, category, description, status, priority, resolution, createdAt,
                LocalDateTime.now());
    }
}
