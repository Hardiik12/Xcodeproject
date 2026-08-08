//
//  ContentCard.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct ContentCard: View {
    let item: ContentItem
    let action: () -> Void
    
    public init(item: ContentItem, action: @escaping () -> Void) {
        self.item = item
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: AppSpacing.xSmall) {
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                        .fill(AppColors.cardSurface)
                        .aspectRatio(16/9, contentMode: .fit)
                        .overlay(
                            VStack {
                                Image(systemName: iconName(for: item.type))
                                    .font(.system(size: 28))
                                    .foregroundStyle(AppColors.primary)
                            }
                        )
                    
                    Text(item.language)
                        .font(AppTypography.badge)
                        .padding(.horizontal, AppSpacing.xSmall)
                        .padding(.vertical, AppSpacing.xxSmall)
                        .background(AppColors.secondary)
                        .foregroundStyle(AppColors.textPrimary)
                        .cornerRadius(AppSpacing.CornerRadius.small)
                        .padding(AppSpacing.xSmall)
                }
                
                Text(item.title)
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.textPrimary)
                    .lineLimit(1)
                
                Text(item.category)
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textSecondary)
                    .lineLimit(1)
            }
            .frame(width: 180)
        }
        .buttonStyle(.plain)
    }
    
    private func iconName(for type: ContentType) -> String {
        switch type {
        case .documentary: return "film"
        case .podcast: return "mic"
        case .entertainment: return "play.tv"
        case .education: return "book"
        case .story: return "person.3"
        }
    }
}
