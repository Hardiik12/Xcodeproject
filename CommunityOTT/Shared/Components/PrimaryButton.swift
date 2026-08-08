//
//  PrimaryButton.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct PrimaryButton: View {
    let title: String
    let iconSystemName: String?
    let action: () -> Void
    
    public init(title: String, iconSystemName: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.iconSystemName = iconSystemName
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            HStack(spacing: AppSpacing.xSmall) {
                if let iconSystemName {
                    Image(systemName: iconSystemName)
                        .font(AppTypography.headline)
                }
                Text(title)
                    .font(AppTypography.headline)
            }
            .foregroundStyle(Color.black)
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.medium)
            .background(AppColors.primary)
            .cornerRadius(AppSpacing.CornerRadius.medium)
        }
        .buttonStyle(.plain)
    }
}
