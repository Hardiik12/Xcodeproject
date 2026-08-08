//
//  LandscapeCardView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct LandscapeCardView: View {
    let item: ContentItem
    let action: () -> Void
    
    public init(item: ContentItem, action: @escaping () -> Void) {
        self.item = item
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                // 16:9 Landscape Card Surface
                ZStack(alignment: .bottomTrailing) {
                    ZStack {
                        if let imageName = item.imageName {
                            Image(imageName)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } else {
                            LinearGradient(
                                colors: [AppColors.secondary, AppColors.cardSurface],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        }
                    }
                    .frame(width: 190, height: 107) // 16:9 aspect ratio
                    .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                    
                    Text(item.durationFormatted)
                        .font(AppTypography.badge)
                        .padding(.horizontal, AppSpacing.xxSmall + 2)
                        .padding(.vertical, AppSpacing.xxSmall)
                        .background(Color.black.opacity(0.75))
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
            .frame(width: 190)
        }
        .buttonStyle(.cardPress)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.title), \(item.category)")
    }
}
