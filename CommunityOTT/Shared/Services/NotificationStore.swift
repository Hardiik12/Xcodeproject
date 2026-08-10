//
//  NotificationStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation
import Combine

public final class NotificationStore: ObservableObject, @unchecked Sendable {
    public static let shared = NotificationStore()
    
    private static let readIDsKey = "communityott_read_notification_ids"
    
    @Published public private(set) var notifications: [AppNotification] = []
    @Published public private(set) var unreadCount: Int = 0
    
    private var readIDs: Set<String> {
        didSet {
            UserDefaults.standard.set(Array(readIDs), forKey: Self.readIDsKey)
            updateNotificationsReadState()
        }
    }
    
    private init() {
        let savedIDs = UserDefaults.standard.stringArray(forKey: Self.readIDsKey) ?? []
        self.readIDs = Set(savedIDs)
        self.notifications = Self.defaultNotifications
        updateNotificationsReadState()
    }
    
    public func markAsRead(id: String) {
        DispatchQueue.main.async { [weak self] in
            self?.readIDs.insert(id)
        }
    }
    
    public func markAllAsRead() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let allIDs = self.notifications.map { $0.id }
            self.readIDs.formUnion(allIDs)
        }
    }
    
    private func updateNotificationsReadState() {
        var updated = notifications
        for i in 0..<updated.count {
            updated[i].isRead = readIDs.contains(updated[i].id)
        }
        self.notifications = updated
        self.unreadCount = updated.filter { !$0.isRead }.count
    }
    
    // MARK: - Mock Notifications
    
    public static let defaultNotifications: [AppNotification] = {
        let now = Date()
        let calendar = Calendar.current
        let yesterday = calendar.date(byAdding: .day, value: -1, to: now) ?? now
        let earlier = calendar.date(byAdding: .day, value: -3, to: now) ?? now
        
        return [
            AppNotification(
                id: "notif_1",
                title: "New Documentary",
                message: "\"Stories of Our Heritage\" is now available to stream.",
                date: now.addingTimeInterval(-3600 * 2), // 2 hours ago
                type: .content,
                isRead: false,
                contentID: "item_1"
            ),
            AppNotification(
                id: "notif_2",
                title: "Voices of Success",
                message: "A new episode featuring local entrepreneurs is now live.",
                date: now.addingTimeInterval(-3600 * 5), // 5 hours ago
                type: .podcast,
                isRead: false,
                contentID: "item_4"
            ),
            AppNotification(
                id: "notif_3",
                title: "Folk & Culture",
                message: "Discover a new traditional performance recorded live.",
                date: yesterday.addingTimeInterval(3600 * 4),
                type: .culture,
                isRead: false,
                contentID: "item_2"
            ),
            AppNotification(
                id: "notif_4",
                title: "Education & Skills",
                message: "New learning content on digital literacy has been added.",
                date: yesterday,
                type: .education,
                isRead: false,
                contentID: "item_3"
            ),
            AppNotification(
                id: "notif_5",
                title: "Welcome to CommunityOTT",
                message: "Explore regional stories, podcasts, and cultural archives.",
                date: earlier,
                type: .system,
                isRead: false,
                contentID: nil
            ),
            AppNotification(
                id: "notif_6",
                title: "Podcast Series Update",
                message: "Listen to the latest community discussion on rural innovation.",
                date: earlier.addingTimeInterval(-3600 * 12),
                type: .podcast,
                isRead: false,
                contentID: "item_5"
            )
        ]
    }()
}
