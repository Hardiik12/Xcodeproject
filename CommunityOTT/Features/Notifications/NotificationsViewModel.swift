//
//  NotificationsViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI
import Combine

public struct NotificationSection: Identifiable {
    public let id: String
    public let title: String
    public let notifications: [AppNotification]
}

@MainActor
public final class NotificationsViewModel: ObservableObject {
    @Published public private(set) var sections: [NotificationSection] = []
    @Published public private(set) var hasUnread: Bool = false
    @Published public var selectedContentItem: ContentItem?
    
    private let store: NotificationStore
    private let repository: ContentRepositoryProtocol
    private var cancellables = Set<AnyCancellable>()
    
    public init(
        store: NotificationStore = NotificationStore.shared,
        repository: ContentRepositoryProtocol = MockContentRepository()
    ) {
        self.store = store
        self.repository = repository
        setupSubscription()
    }
    
    public func markAsRead(_ notification: AppNotification) {
        store.markAsRead(id: notification.id)
        
        if let contentID = notification.contentID {
            Task {
                do {
                    let item = try await repository.getContentItem(id: contentID)
                    self.selectedContentItem = item
                } catch {
                    print("Could not load item for notification: \(error)")
                }
            }
        }
    }
    
    public func markAllAsRead() {
        store.markAllAsRead()
    }
    
    private func setupSubscription() {
        store.$notifications
            .receive(on: DispatchQueue.main)
            .sink { [weak self] notifications in
                self?.buildSections(from: notifications)
                self?.hasUnread = notifications.contains { !$0.isRead }
            }
            .store(in: &cancellables)
    }
    
    private func buildSections(from notifications: [AppNotification]) {
        let calendar = Calendar.current
        
        var today: [AppNotification] = []
        var yesterday: [AppNotification] = []
        var earlier: [AppNotification] = []
        
        for notif in notifications {
            if calendar.isDateInToday(notif.date) {
                today.append(notif)
            } else if calendar.isDateInYesterday(notif.date) {
                yesterday.append(notif)
            } else {
                earlier.append(notif)
            }
        }
        
        var result: [NotificationSection] = []
        if !today.isEmpty {
            result.append(NotificationSection(id: "today", title: "Today", notifications: today))
        }
        if !yesterday.isEmpty {
            result.append(NotificationSection(id: "yesterday", title: "Yesterday", notifications: yesterday))
        }
        if !earlier.isEmpty {
            result.append(NotificationSection(id: "earlier", title: "Earlier", notifications: earlier))
        }
        
        self.sections = result
    }
}
