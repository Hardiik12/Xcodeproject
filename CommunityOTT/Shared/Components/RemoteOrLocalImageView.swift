//
//  RemoteOrLocalImageView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct RemoteOrLocalImageView: View {
    let imageSource: String
    let contentMode: ContentMode
    
    public init(imageSource: String, contentMode: ContentMode = .fill) {
        self.imageSource = imageSource
        self.contentMode = contentMode
    }
    
    public var body: some View {
        if imageSource.lowercased().hasPrefix("http://") || imageSource.lowercased().hasPrefix("https://") {
            if let url = URL(string: imageSource) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        ZStack {
                            AppColors.cardSurface
                            ProgressView()
                                .tint(AppColors.primary)
                        }
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: contentMode)
                    case .failure:
                        ZStack {
                            AppColors.cardSurface
                            Image(systemName: "photo")
                                .font(.system(size: 32))
                                .foregroundStyle(AppColors.textMuted)
                        }
                    @unknown default:
                        AppColors.cardSurface
                    }
                }
            } else {
                fallbackImage
            }
        } else {
            Image(imageSource)
                .resizable()
                .aspectRatio(contentMode: contentMode)
        }
    }
    
    private var fallbackImage: some View {
        ZStack {
            AppColors.cardSurface
            Image(systemName: "photo")
                .font(.system(size: 32))
                .foregroundStyle(AppColors.textMuted)
        }
    }
}
