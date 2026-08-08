//
//  CategoryGridView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct CategoryGridView: View {
    let categories: [ContentCategory]
    let onSelectCategory: (ContentCategory) -> Void
    
    private let columns = [
        GridItem(.flexible(), spacing: AppSpacing.medium),
        GridItem(.flexible(), spacing: AppSpacing.medium)
    ]
    
    public init(categories: [ContentCategory], onSelectCategory: @escaping (ContentCategory) -> Void) {
        self.categories = categories
        self.onSelectCategory = onSelectCategory
    }
    
    public var body: some View {
        LazyVGrid(columns: columns, spacing: AppSpacing.medium) {
            ForEach(categories) { category in
                CategoryCardView(category: category) {
                    onSelectCategory(category)
                }
            }
        }
    }
}

public struct CategoryCardView: View {
    let category: ContentCategory
    let action: () -> Void
    
    public init(category: ContentCategory, action: @escaping () -> Void) {
        self.category = category
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomLeading) {
                // Background Artwork Image
                Image(category.imageName)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(height: 140)
                    .clipped()
                
                // Dark Cinematic Gradient Overlay
                LinearGradient(
                    colors: [
                        Color.black.opacity(0.15),
                        Color.black.opacity(0.75),
                        AppColors.background.opacity(0.95)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                
                // Accent Border Highlight
                RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                    .stroke(
                        LinearGradient(
                            colors: [AppColors.primary.opacity(0.4), AppColors.secondary.opacity(0.2)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
                
                // Content Label Overlay
                VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                    Text(category.title)
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.textPrimary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    
                    if !category.description.isEmpty {
                        Text(category.description)
                            .font(AppTypography.caption)
                            .foregroundStyle(AppColors.textSecondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                }
                .padding(AppSpacing.small)
            }
            .frame(height: 140)
            .background(AppColors.cardSurface)
            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
            .shadow(color: Color.black.opacity(0.4), radius: 6, x: 0, y: 3)
        }
        .buttonStyle(CardPressButtonStyle())
        .accessibilityLabel("\(category.title) category")
        .accessibilityHint(category.description)
    }
}
