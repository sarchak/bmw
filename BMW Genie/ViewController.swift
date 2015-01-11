//
//  ViewController.swift
//  BMW Genie
//
//  Created by Shrikar Archak on 1/10/15.
//  Copyright (c) 2015 Shrikar Archak. All rights reserved.
//

import UIKit
import MapKit


class ViewController: UIViewController {

    let green = UIColor(red: 154.0/255, green : 208.0/255, blue: 99.0/255, alpha: 1)
    let orange = UIColor(red: 253.0/255, green : 190.0/255, blue: 74.0/255, alpha: 1)
    let red = UIColor(red: 215.0/255 , green: 118.0/255, blue: 93.0/255, alpha: 1)
    
    
    
    @IBOutlet weak var diagnosticsView: UIView!
    @IBOutlet weak var batteryView: UIView!
    @IBOutlet weak var fuelView: UIView!
    
    var timer : NSTimer?
    let session = NSURLSession.sharedSession()
    let API_KEY = "f609f0d1-0a37-4bfc-95fd-4b86c4b3ee77"
    var presented: Bool = false
    @IBOutlet weak var fuelLabel: UILabel!
    @IBOutlet weak var batteryLabel: UILabel!
    override func viewDidLoad() {
        super.viewDidLoad()
        var timer = NSTimer.scheduledTimerWithTimeInterval(2, target: self, selector: Selector("update"), userInfo: nil, repeats: true)
    }


    @IBAction func takeMeThere(sender: AnyObject) {
        println("Take me there")
        let latitude = 37.566150665283200
        let longitude = -122.324226379394530
        let placemark  = MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), addressDictionary: nil)
        let mapItem = MKMapItem(placemark: placemark)
        mapItem.name = "Charing Station"
        let options = [
                MKLaunchOptionsDirectionsModeKey : MKLaunchOptionsDirectionsModeDriving,
                MKLaunchOptionsShowsTrafficKey: false
            ]
        mapItem.openInMapsWithLaunchOptions(options)
    }
    func getColor(value : Int) -> UIColor {
        let color = UIColor.greenColor()
        if(value > 75) {
            return green
        } else if(value > 25 && value < 75) {
            return orange
        } else if( value < 25) {
            return red
        } else {
            return green
        }

        return color
    }
    
    func update() {
        let baseURL = NSURL(string: "http://data.api.hackthedrive.com/v1/Vehicles/233d57f8-60f1-44e6-a115-2253a0623019")
        let request = NSMutableURLRequest(URL: baseURL!)
        request.addValue(API_KEY, forHTTPHeaderField: "MojioAPIToken")
        
        let downloadTask = session.downloadTaskWithRequest(request, completionHandler: { (location, response, error) -> Void in
            if(error == nil){
                let objectData = NSData(contentsOfURL: location)
                let tmpData :NSString = NSString(data: objectData!, encoding: NSUTF8StringEncoding)!
                var err: NSError
                var jsonResult: NSDictionary = NSJSONSerialization.JSONObjectWithData(objectData!, options: NSJSONReadingOptions.MutableContainers, error: nil) as NSDictionary

                var level = 100
                if( jsonResult.objectForKey("LastBatteryLevel") != nil) {
                    level = jsonResult.objectForKey("LastBatteryLevel") as Int
                }

                println("Battery level : \(level)")
                let fuellevel = jsonResult.objectForKey("FuelLevel") as Int
                println("Fuel level : \(fuellevel)")


                    dispatch_async(dispatch_get_main_queue(), { () -> Void in
                        if( fuellevel < 10 && self.presented == false) {
                            self.presented = true
                            let alertViewController = UIAlertController(title: "Low Battery", message: "Range is 40miles", preferredStyle: .Alert)
                            let okButton =  UIAlertAction(title: "OK", style: UIAlertActionStyle.Default, handler: { (action) -> Void in
                                println("Ok Pressed")
                            })
                            let cancelButton = UIAlertAction(title: "Cancel", style: .Cancel, handler: nil)
                            alertViewController.addAction(okButton)
                            alertViewController.addAction(cancelButton)
                            self.presentViewController(alertViewController, animated: true, completion: nil)
                        }
                        UIView.animateWithDuration(1.0, animations: { () -> Void in
                            self.batteryView.backgroundColor = self.getColor(level)
                            self.fuelView.backgroundColor = self.getColor(fuellevel)
                            
                        })
                    })

            } else {
                let alertViewController = UIAlertController(title: "Error", message: "Couldn't connect to network", preferredStyle: .Alert)
                let okButton = UIAlertAction(title: "OK", style: .Default, handler: nil)
                let cancelButton = UIAlertAction(title: "Cancel", style: .Cancel, handler: nil)
                alertViewController.addAction(okButton)
                alertViewController.addAction(cancelButton)
                self.presentViewController(alertViewController, animated: true, completion: nil)
                dispatch_async(dispatch_get_main_queue(), { () -> Void in
                })
            }
        })
        downloadTask.resume()

    }
    
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }


}

