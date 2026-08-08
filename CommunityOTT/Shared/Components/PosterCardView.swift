//
//  PosterCardView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct PosterCardView: View {
    let item: ContentItem
    let action: () -> Void
    
    public init(item: ContentItem, action: @escaping () -> Void) {
        self.item = item
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                // 2:3 Vertical Poster Card Surface
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                        .fill(AppColors.cardSurface)
                        .aspectRatio(2/3, contentMode: .fit)
                        .overlay(
                            ZStack {
                                LinearGradient(
                                    colors: [AppColors.secondary.opacity(0.6), AppColors.elevatedCard],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                
                                VStack(spacing: AppSpacing.xSmall) {
                                    Image(systemName: "film.fill")
                                        .font(.system(size: 36))
                                        .foregroundStyle(AppColors.primary.opacity(0.8))
                                    
                                    Text(item.title)
                                        .font(AppTypography.caption)
                                        .foregroundStyle(AppColors.textPrimary)
                                        .multilineTextAlignment(.center)
                                        .padding(.horizontal, AppSpacing.xSmall)
                                }
                            }
                        )
                        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                    
                    // Language Badge
                    Text(item.language)
                        .font(AppTypography.badge)
                        .padding(.horizontal, AppSpacing.xxSmall + 2)
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
            .frame(width: 140)
        }
        .buttonStyle(.cardPress)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.title), \(item.category), \(item.language)")
    }
}

#Preview {
    PosterCardView(
        item: ContentItem(
            id: "1",
            title: "Weavers of Pochampally",
            description: "Ikat heritage",
            category: "Documentary",
            type: .documentary,
            language: "Telugu"
        ),
        action: {}
    )
}
