//
//  PlaybackProgressStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation
import Combine

public final class PlaybackProgressStore: ObservableObject, @unchecked Sendable {
    public static let shared = PlaybackProgressStore()
    
    @Published public private(set) var savedProgress: [String: Double] = [:]
    
    private init() {}
    
    public func saveProgress(contentID: String, positionInSeconds: Double, totalDurationInSeconds: Double) {
        guard totalDurationInSeconds > 0 else { return }
        let percentage = min(max(positionInSeconds / totalDurationInSeconds, 0.0), 1.0)
        DispatchQueue.main.async {
            self.savedProgress[contentID] = percentage
        }
    }
    
    public func getProgress(for contentID: String) -> Double? {
        savedProgress[contentID]
    }
}
