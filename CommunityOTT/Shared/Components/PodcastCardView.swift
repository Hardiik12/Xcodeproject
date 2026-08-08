//
//  PodcastCardView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct PodcastCardView: View {
    let item: ContentItem
    let action: () -> Void
    
    public init(item: ContentItem, action: @escaping () -> Void) {
        self.item = item
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                // 1:1 Square Podcast Artwork
                ZStack(alignment: .bottomLeading) {
                    ZStack {
                        if let imageName = item.imageName {
                            Image(imageName)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } else {
                            LinearGradient(
                                colors: [AppColors.primary.opacity(0.3), AppColors.secondary],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        }
                    }
                    .frame(width: 135, height: 135) // 1:1 aspect ratio
                    .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                    
                    Text("PODCAST")
                        .font(AppTypography.badge)
                        .padding(.horizontal, AppSpacing.xxSmall + 2)
                        .padding(.vertical, AppSpacing.xxSmall)
                        .background(Color.black.opacity(0.7))
                        .foregroundStyle(AppColors.primary)
                        .cornerRadius(AppSpacing.CornerRadius.small)
                        .padding(AppSpacing.xSmall)
                }
                
                Text(item.title)
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.textPrimary)
                    .lineLimit(1)
                
                Text(item.durationFormatted)
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textSecondary)
                    .lineLimit(1)
            }
            .frame(width: 135)
        }
        .buttonStyle(.cardPress)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Podcast: \(item.title), duration \(item.durationFormatted)")
    }
}
