import '../dm.dart';

/// UC-D7, D28: one alert rule as the DM sets it; [min] and [max] are the server's bounds.
class RuleSetting {
  const RuleSetting({
    required this.type,
    required this.count,
    required this.active,
    required this.min,
    required this.max,
  });

  factory RuleSetting.fromJson(Map<String, dynamic> json) => RuleSetting(
    type: json['type'] as String,
    count: json['count'] as int,
    active: json['active'] as bool,
    min: json['min'] as int,
    max: json['max'] as int,
  );

  /// As the server names it: `FULL_DAY_ABSENCE`.
  final String type;
  final int count;
  final bool active;
  final int min;
  final int max;

  AlertRule get rule => AlertRule.parse(type);

  RuleSetting copyWith({int? count, bool? active}) =>
      RuleSetting(type: type, count: count ?? this.count, active: active ?? this.active, min: min, max: max);

  Map<String, dynamic> toJson() => {'type': type, 'count': count, 'active': active};
}

/// The settings form: times as "HH:mm". Its bounds are the server's too (D28), so a time out of
/// them is a typing error shown before saving.
class SettingsDraft {
  const SettingsDraft({required this.startTime, required this.digestTimes, required this.rules});

  factory SettingsDraft.fromJson(Map<String, dynamic> json) => SettingsDraft(
    startTime: _hm(json['startTime'] as String),
    digestTimes: [for (final t in json['digestTimes'] as List) _hm(t as String)],
    rules: [for (final r in (json['rules'] as List).cast<Map<String, dynamic>>()) RuleSetting.fromJson(r)],
  );

  static const startBounds = (min: '05:00', max: '12:00');
  static const digestBounds = (min: '06:00', max: '20:00');

  /// A second digest starts here, as on the web.
  static const secondDigest = '18:00';

  final String startTime;

  /// One or two, the second later than the first.
  final List<String> digestTimes;
  final List<RuleSetting> rules;

  bool get startOutOfBounds => !_within(startTime, startBounds);

  bool digestOutOfBounds(int i) => !_within(digestTimes[i], digestBounds);

  bool digestOutOfOrder(int i) => i == 1 && digestTimes[1].compareTo(digestTimes[0]) <= 0;

  bool get valid =>
      !startOutOfBounds &&
      [for (var i = 0; i < digestTimes.length; i++) i].every((i) => !digestOutOfBounds(i) && !digestOutOfOrder(i)) &&
      rules.every((r) => r.count >= r.min && r.count <= r.max);

  SettingsDraft copyWith({String? startTime, List<String>? digestTimes, List<RuleSetting>? rules}) => SettingsDraft(
    startTime: startTime ?? this.startTime,
    digestTimes: digestTimes ?? this.digestTimes,
    rules: rules ?? this.rules,
  );

  Map<String, dynamic> toJson() => {
    'startTime': startTime,
    'digestTimes': digestTimes,
    'rules': [for (final r in rules) r.toJson()],
  };
}

/// "07:30:00" -> "07:30"; "HH:mm" strings compare as times.
String _hm(String time) => time.substring(0, 5);

bool _within(String time, ({String min, String max}) bounds) =>
    time.compareTo(bounds.min) >= 0 && time.compareTo(bounds.max) <= 0;
