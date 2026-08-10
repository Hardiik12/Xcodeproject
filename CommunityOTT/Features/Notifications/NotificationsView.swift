//
//  NotificationsView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct NotificationsView: View {
    @StateObject private var viewModel = NotificationsViewModel()
    @Environment(\.dismiss) private var dismiss
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                if viewModel.sections.isEmpty {
                    EmptyStateView(
                        title: "You're all caught up.",
                        description: "New stories, podcasts and community updates will appear here.",
                        iconSystemName: "bell.slash.fill"
                    )
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: AppSpacing.large) {
                            ForEach(viewModel.sections) { section in
                                VStack(alignment: .leading, spacing: AppSpacing.small) {
                                    Text(section.title)
                                        .font(AppTypography.headline)
                                        .foregroundStyle(AppColors.primary)
                                        .padding(.horizontal, AppSpacing.medium)
                                    
                                    VStack(spacing: AppSpacing.small) {
                                        ForEach(section.notifications) { notif in
                                            notificationRow(notif)
                                        }
                                    }
                                    .padding(.horizontal, AppSpacing.medium)
                                }
                            }
                        }
                        .padding(.vertical, AppSpacing.medium)
                        .padding(.bottom, 40)
                    }
                }
            }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(AppColors.textSecondary)
                    }
                    .accessibilityLabel("Close notifications screen")
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    if viewModel.hasUnread {
                        Button("Mark All as Read") {
                            viewModel.markAllAsRead()
                        }
                        .font(AppTypography.caption)
                        .foregroundStyle(AppColors.primary)
                        .accessibilityLabel("Mark all notifications as read")
                    }
                }
            }
            .sheet(item: $viewModel.selectedContentItem) { item in
                ContentDetailsView(item: item)
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private func notificationRow(_ notif: AppNotification) -> some View {
        Button {
            viewModel.markAsRead(notif)
        } label: {
            HStack(alignment: .top, spacing: AppSpacing.medium) {
                // Icon Container
                ZStack {
                    Circle()
                        .fill(notif.isRead ? Color.white.opacity(0.05) : AppColors.primary.opacity(0.15))
                        .frame(width: 42, height: 42)
                    
                    Image(systemName: notif.type.iconName)
                        .font(.system(size: 18))
                        .foregroundStyle(notif.isRead ? AppColors.textSecondary : AppColors.primary)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(notif.title)
                            .font(AppTypography.headline)
                            .foregroundStyle(notif.isRead ? AppColors.textSecondary : AppColors.textPrimary)
                        
                        Spacer()
                        
                        Text(timeAgoString(from: notif.date))
                            .font(AppTypography.caption)
                            .foregroundStyle(AppColors.textMuted)
                    }
                    
                    Text(notif.message)
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.textSecondary)
                        .multilineTextAlignment(.leading)
                }
                
                if !notif.isRead {
                    Circle()
                        .fill(AppColors.primary)
                        .frame(width: 8, height: 8)
                        .padding(.top, 4)
                }
            }
            .padding(AppSpacing.medium)
            .background(notif.isRead ? AppColors.cardSurface.opacity(0.6) : AppColors.cardSurface)
            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
            .overlay(
                RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                    .stroke(notif.isRead ? Color.white.opacity(0.05) : AppColors.primary.opacity(0.4), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(notif.title), \(notif.isRead ? "read" : "unread") notification")
    }
    
    private func timeAgoString(from date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
