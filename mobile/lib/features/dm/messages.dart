import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'dm.dart';

/// UC-D4, D38: what the DM is writing. [id] is made once per composer, so sending again after a
/// lost answer or a double tap sends once: the server is idempotent on it.
class MessageDraft {
  const MessageDraft({
    required this.id,
    this.studentId,
    this.classId,
    required this.body,
    required this.sms,
    required this.plainSms,
  });

  final String id;

  /// One of the two: the guardians of one student, or of one class.
  final String? studentId;
  final String? classId;
  final String body;

  /// Also by SMS for the guardians without the app.
  final bool sms;

  /// Accents and special letters replaced in the SMS only (the push keeps them).
  final bool plainSms;

  Map<String, dynamic> toJson() => {
    'id': id,
    'studentId': ?studentId,
    'classId': ?classId,
    'body': body.trim(),
    'sms': sms,
    'plainSms': plainSms,
  };
}

/// D38: who would get the message and what its SMS would cost; nothing is sent.
class MessagePreview {
  const MessagePreview({
    required this.recipients,
    required this.byApp,
    required this.bySms,
    required this.unreached,
    required this.smsUnits,
    required this.smsUnicode,
    required this.smsLeft,
    required this.estimatedCost,
    required this.overCap,
    required this.smsSample,
  });

  factory MessagePreview.fromJson(Map<String, dynamic> json) => MessagePreview(
    recipients: json['recipients'] as int,
    byApp: json['byApp'] as int,
    bySms: json['bySms'] as int,
    unreached: json['unreached'] as int,
    smsUnits: json['smsUnits'] as int,
    smsUnicode: json['smsUnicode'] as bool,
    smsLeft: json['smsLeft'] as int,
    estimatedCost: json['estimatedCost'] as int,
    overCap: json['overCap'] as bool,
    smsSample: json['smsSample'] as String?,
  );

  /// Guardians, each once even with several children in the audience.
  final int recipients;
  final int byApp;
  final int bySms;

  /// Without the app and without SMS: they get nothing.
  final int unreached;

  /// SMS units of the author's language version (GSM-7 160/153, otherwise 70/67).
  final int smsUnits;
  final bool smsUnicode;

  /// What the month has left (BR-25), queued SMS counted.
  final int smsLeft;

  /// In FCFA, each guardian's SMS in their language at the school's price.
  final int estimatedCost;
  final bool overCap;

  /// The SMS as the author's language reads it; null without SMS.
  final String? smsSample;
}

enum MessageAudience {
  student,
  schoolClass,
  school;

  static MessageAudience parse(String? wire) => switch (wire) {
    'STUDENT' => student,
    'CLASS' => schoolClass,
    _ => school,
  };
}

/// D38: what became of a message, one count per guardian and channel.
class Delivery {
  const Delivery({
    required this.recipients,
    required this.pending,
    required this.sent,
    required this.delivered,
    required this.opened,
    required this.failed,
  });

  factory Delivery.fromJson(Map<String, dynamic> json) => Delivery(
    recipients: json['recipients'] as int,
    pending: json['pending'] as int,
    sent: json['sent'] as int,
    delivered: json['delivered'] as int,
    opened: json['opened'] as int,
    failed: json['failed'] as int,
  );

  final int recipients;
  final int pending;
  final int sent;
  final int delivered;
  final int opened;
  final int failed;
}

/// D38: one message the DM sent. Never edited or deleted. No phone numbers (NFR-9).
class SentMessage {
  const SentMessage({
    required this.id,
    required this.audience,
    required this.studentName,
    required this.className,
    required this.body,
    required this.sms,
    required this.createdAt,
    required this.delivery,
  });

  factory SentMessage.fromJson(Map<String, dynamic> json) => SentMessage(
    id: json['id'] as String,
    audience: MessageAudience.parse(json['audience'] as String?),
    studentName: json['studentName'] as String?,
    className: json['className'] as String?,
    body: json['body'] as String,
    sms: json['sms'] as bool,
    createdAt: DateTime.parse(json['createdAt'] as String).toLocal(),
    delivery: Delivery.fromJson((json['delivery'] as Map).cast<String, dynamic>()),
  );

  final String id;
  final MessageAudience audience;
  final String? studentName;
  final String? className;
  final String body;
  final bool sms;
  final DateTime createdAt;
  final Delivery delivery;
}

/// D38: a class the author may write to.
class ClassOption {
  const ClassOption({required this.classId, required this.className, required this.students});

  factory ClassOption.fromJson(Map<String, dynamic> json) => ClassOption(
    classId: json['classId'] as String,
    className: json['className'] as String,
    students: json['students'] as int,
  );

  final String classId;
  final String className;
  final int students;
}

/// UC-D4: the DM's own messages, newest first.
final sentMessagesProvider = FutureProvider.autoDispose<List<SentMessage>>(
  (ref) => ref.watch(dmRepositoryProvider).sentMessages(),
);

/// D38: the classes of the DM's blocks.
final messageClassesProvider = FutureProvider.autoDispose<List<ClassOption>>(
  (ref) => ref.watch(dmRepositoryProvider).messageClasses(),
);
