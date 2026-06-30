package com.dididi.booking.social.api.dto;

import java.util.List;

/** Binh luan da do san; replies = danh sach tra loi (1 cap). Class de Thymeleaf doc qua getter. */
public class CommentView {

    private final Long id;
    private final ActorView author;
    private final String content;
    private final long createdAtMs;
    private final Long parentId;
    private final boolean canDelete;
    private final List<CommentView> replies;

    public CommentView(Long id, ActorView author, String content, long createdAtMs, Long parentId,
                       boolean canDelete, List<CommentView> replies) {
        this.id = id;
        this.author = author;
        this.content = content;
        this.createdAtMs = createdAtMs;
        this.parentId = parentId;
        this.canDelete = canDelete;
        this.replies = replies;
    }

    public Long getId() { return id; }
    public ActorView getAuthor() { return author; }
    public String getContent() { return content; }
    public long getCreatedAtMs() { return createdAtMs; }
    public Long getParentId() { return parentId; }
    public boolean isCanDelete() { return canDelete; }
    public List<CommentView> getReplies() { return replies; }

    // ----- P2: thich binh luan (gan bang setter) -----
    private boolean liked;
    private int likeCount;

    public boolean isLiked() { return liked; }
    public void setLiked(boolean liked) { this.liked = liked; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    // ----- E3: postId (cho form xoa trong fragment node binh luan realtime) -----
    private Long postId;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
}
