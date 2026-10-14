// xcode: set sdk=iOS

import ActivityKit
import Foundation

public struct NexioCourseActivityAttributes: ActivityAttributes, Codable {
    public struct ContentState: Codable, Hashable {
        /// Target end timestamp in epoch seconds.
        public var endEpochSeconds: Double
        /// Classroom location.
        public var classroom: String
        /// Section text, e.g. "Section 3-4".
        public var section: String
        /// false = pre-class countdown, true = in-class progress.
        public var isInClass: Bool
        /// Left slot mode: 0=course name, 1=classroom, 2=countdown.
        public var leftMode: Int
        /// Right slot mode: 0=course name, 1=classroom, 2=countdown.
        public var rightMode: Int
        /// In-class right slot mode: 0=in-class status text, 1=countdown until bell.
        public var inClassRightMode: Int

        public init(
            endEpochSeconds: Double,
            classroom: String,
            section: String,
            isInClass: Bool,
            leftMode: Int = 0,
            rightMode: Int = 2,
            inClassRightMode: Int = 1
        ) {
            self.endEpochSeconds = endEpochSeconds
            self.classroom = classroom
            self.section = section
            self.isInClass = isInClass
            self.leftMode = leftMode
            self.rightMode = rightMode
            self.inClassRightMode = inClassRightMode
        }
    }

    public var courseId: String
    public var courseName: String
    public var startEpochSeconds: Double

    public init(courseId: String, courseName: String, startEpochSeconds: Double) {
        self.courseId = courseId
        self.courseName = courseName
        self.startEpochSeconds = startEpochSeconds
    }
}
