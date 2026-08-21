package com.communityott.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight unread alert count response DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountResponse {
    private long unreadCount;
}
