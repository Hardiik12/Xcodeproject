//
//  PlaybackProgressStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation
import Combine

public struct PlaybackProgressRecord: Codable, Equatable, Sendable {
    public let contentID: String
    public let currentTime: Double
    public let duration: Double
    public let lastUpdated: Date
    
    public var completionPercentage: Double {
        guard duration > 0 else { return 0.0 }
        return min(max(currentTime / duration, 0.0), 1.0)
    }
    
    public var isCompleted: Bool {
        completionPercentage >= 0.95
    }
    
    public init(contentID: String, currentTime: Double, duration: Double, lastUpdated: Date = Date()) {
        self.contentID = contentID
        self.currentTime = currentTime
        self.duration = duration
        self.lastUpdated = lastUpdated
    }
}

public final class PlaybackProgressStore: ObservableObject, @unchecked Sendable {
    public static let shared = PlaybackProgressStore()
    
    private static let userDefaultsKey = "communityott_playback_progress"
    
    @Published public private(set) var savedRecords: [String: PlaybackProgressRecord] = [:]
    
    private init() {
        loadRecordsFromUserDefaults()
    }
    
    // MARK: - Persistence API
    
    public func saveProgress(contentID: String, currentTime: Double, duration: Double) {
        guard duration > 0 else { return }
        
        let record = PlaybackProgressRecord(
            contentID: contentID,
            currentTime: currentTime,
            duration: duration,
            lastUpdated: Date()
        )
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.savedRecords[contentID] = record
            self.persistToUserDefaults()
        }
    }
    
    public func getRecord(for contentID: String) -> PlaybackProgressRecord? {
        savedRecords[contentID]
    }
    
    public func getProgressRatio(for contentID: String) -> Double? {
        guard let record = savedRecords[contentID] else { return nil }
        return record.completionPercentage
    }
    
    public func isCompleted(for contentID: String) -> Bool {
        savedRecords[contentID]?.isCompleted ?? false
    }
    
    public func removeProgress(for contentID: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.savedRecords.removeValue(forKey: contentID)
            self.persistToUserDefaults()
        }
    }
    
    // MARK: - Private Helpers
    
    private func loadRecordsFromUserDefaults() {
        guard let data = UserDefaults.standard.data(forKey: Self.userDefaultsKey) else { return }
        do {
            let decoded = try JSONDecoder().decode([String: PlaybackProgressRecord].self, from: data)
            self.savedRecords = decoded
        } catch {
            print("PlaybackProgressStore load error: \(error)")
        }
    }
    
    private func persistToUserDefaults() {
        do {
            let encoded = try JSONEncoder().encode(savedRecords)
            UserDefaults.standard.set(encoded, forKey: Self.userDefaultsKey)
        } catch {
            print("PlaybackProgressStore save error: \(error)")
        }
    }
}
