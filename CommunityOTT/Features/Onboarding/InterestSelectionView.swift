//
//  InterestSelectionView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct InterestItem: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let systemIcon: String
    
    public init(id: String, title: String, systemIcon: String) {
        self.id = id
        self.title = title
        self.systemIcon = systemIcon
    }
    
    public static let defaultInterests: [InterestItem] = [
        InterestItem(id: "folk", title: "Folk & Cultural Arts", systemIcon: "music.note.house.fill"),
        InterestItem(id: "history", title: "History & Documentaries", systemIcon: "building.columns.fill"),
        InterestItem(id: "empowerment", title: "Empowerment", systemIcon: "bolt.heart.fill"),
        InterestItem(id: "voices", title: "Voices of Success", systemIcon: "star.bubble.fill"),
        InterestItem(id: "podcasts", title: "Podcasts", systemIcon: "mic.fill"),
        InterestItem(id: "education", title: "Education & Skills", systemIcon: "book.closed.fill")
    ]
}

public struct InterestSelectionView: View {
    @Binding var selectedIDs: Set<String>
    
    public init(selectedIDs: Binding<Set<String>>) {
        self._selectedIDs = selectedIDs
    }
    
    public var body: some View {
        VStack(spacing: AppSpacing.large) {
            VStack(spacing: AppSpacing.xxSmall) {
                Text("What would you like to explore?")
                    .font(AppTypography.title2)
                    .foregroundStyle(AppColors.textPrimary)
                    .multilineTextAlignment(.center)
                
                Text("Select categories that match your passions.")
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, AppSpacing.medium)
            
            LazyVGrid(columns: [GridItem(.flexible(), spacing: AppSpacing.medium), GridItem(.flexible(), spacing: AppSpacing.medium)], spacing: AppSpacing.medium) {
                ForEach(InterestItem.defaultInterests) { item in
                    let isSelected = selectedIDs.contains(item.id)
                    
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            if isSelected {
                                selectedIDs.remove(item.id)
                            } else {
                                selectedIDs.insert(item.id)
                            }
                        }
                    } label: {
                        VStack(spacing: AppSpacing.small) {
                            HStack {
                                Image(systemName: item.systemIcon)
                                    .font(.system(size: 24))
                                    .foregroundStyle(isSelected ? AppColors.primary : AppColors.textSecondary)
                                Spacer()
                                if isSelected {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(AppColors.primary)
                                }
                            }
                            
                            Text(item.title)
                                .font(AppTypography.subheadline)
                                .foregroundStyle(isSelected ? AppColors.textPrimary : AppColors.textSecondary)
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(AppSpacing.medium)
                        .frame(height: 100)
                        .background(isSelected ? AppColors.cardSurface : AppColors.cardSurface.opacity(0.5))
                        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                        .overlay(
                            RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                                .stroke(isSelected ? AppColors.primary : Color.white.opacity(0.1), lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Category \(item.title), \(isSelected ? "selected" : "not selected")")
                }
            }
            .padding(.horizontal, AppSpacing.medium)
        }
    }
}
